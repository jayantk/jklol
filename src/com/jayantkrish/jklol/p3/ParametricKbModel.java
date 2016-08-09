package com.jayantkrish.jklol.p3;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.lisp.inc.ParametricContinuationIncEval.StateFeatures;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.ListSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.ParametricFamily;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.models.parametric.TensorSufficientStatistics;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.IndexedList;

public class ParametricKbModel implements ParametricFamily<KbModel> {
  private static final long serialVersionUID = 1L;
  
  private final IndexedList<String> predicateNames;
  private final List<DiscreteVariable> eltFeatureVars;
  private final List<DiscreteVariable> predFeatureVars;

  private final FeatureVectorGenerator<StateFeatures> actionFeatureGen;
  private final DiscreteVariable actionFeatureVar;
  
  public ParametricKbModel(IndexedList<String> predicateNames,
      List<DiscreteVariable> eltFeatureVars, List<DiscreteVariable> predFeatureVars,
      FeatureVectorGenerator<StateFeatures> actionFeatureGen) {
    this.predicateNames = Preconditions.checkNotNull(predicateNames);
    this.eltFeatureVars = Preconditions.checkNotNull(eltFeatureVars);
    this.predFeatureVars = Preconditions.checkNotNull(predFeatureVars);
    Preconditions.checkArgument(eltFeatureVars.size() == predicateNames.size());
    Preconditions.checkArgument(predFeatureVars.size() == predicateNames.size());

    this.actionFeatureGen = actionFeatureGen;
    this.actionFeatureVar = actionFeatureGen.getFeatureDictionary();
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    List<SufficientStatistics> statistics = Lists.newArrayList();
    for (int i = 0; i < predicateNames.size(); i++) {
      statistics.add(paramsFromVar(eltFeatureVars.get(i)));
    }

    List<SufficientStatistics> predStatistics = Lists.newArrayList();
    for (int i = 0; i < predicateNames.size(); i++) {
      predStatistics.add(paramsFromVar(predFeatureVars.get(i)));
    }
    
    SufficientStatistics actionStatistics = paramsFromVar(actionFeatureVar);

    return new ListSufficientStatistics(Arrays.asList("element", "predicate", "action"),
        Arrays.asList(new ListSufficientStatistics(predicateNames, statistics),
            new ListSufficientStatistics(predicateNames, predStatistics),
            actionStatistics));
  }
  
  private static TensorSufficientStatistics paramsFromVar(DiscreteVariable featureVar) {
    return TensorSufficientStatistics.createDense(
          VariableNumMap.singleton(0, featureVar.getName(), featureVar),
          new DenseTensorBuilder(new int[] {0}, new int[] {featureVar.numValues()}));
  }

  private static List<SufficientStatistics> getElementParams(SufficientStatistics params) {
    List<SufficientStatistics> paramList = params.coerceToList().getStatistics();
    return paramList.get(0).coerceToList().getStatistics();
  }

  private static List<SufficientStatistics> getPredicateParams(SufficientStatistics params) {
    List<SufficientStatistics> paramList = params.coerceToList().getStatistics();
    return paramList.get(1).coerceToList().getStatistics();
  }

  private static TensorSufficientStatistics getActionParams(SufficientStatistics params) {
    List<SufficientStatistics> paramList = params.coerceToList().getStatistics();
    return (TensorSufficientStatistics) paramList.get(2);
  }

  @Override
  public KbModel getModelFromParameters(SufficientStatistics parameters) {
    List<SufficientStatistics> eltParameterList = getElementParams(parameters);
    List<SufficientStatistics> predParameterList = getPredicateParams(parameters);

    List<Tensor> classifiers = Lists.newArrayList();
    for (int i = 0; i < predicateNames.size(); i++) {
      classifiers.add(((TensorSufficientStatistics) eltParameterList.get(i)).get());
    }

    List<Tensor> predClassifiers = Lists.newArrayList();
    for (int i = 0; i < predicateNames.size(); i++) {
      predClassifiers.add(((TensorSufficientStatistics) predParameterList.get(i)).get());
    }

    Tensor actionClassifier = getActionParams(parameters).get();
    return new KbModel(predicateNames, classifiers, predClassifiers, actionClassifier,
        actionFeatureGen);
  }

  public void incrementStateSufficientStatistics(SufficientStatistics gradient,
      SufficientStatistics currentParameters, KbState kbState, double count) {
    List<SufficientStatistics> eltGradientList = getElementParams(gradient);
    List<SufficientStatistics> predGradientList = getPredicateParams(gradient);

    IndexedList<String> stateFunctionNames = kbState.getFunctions();
    List<FunctionAssignment> assignments = kbState.getAssignments();
    for (int i : kbState.getUpdatedFunctionIndexes()) {
      int index = predicateNames.getIndex(stateFunctionNames.get(i));

      TensorSufficientStatistics eltGradient = (TensorSufficientStatistics) eltGradientList.get(index);
      eltGradient.increment(assignments.get(i).getFeatureVector(), count);

      TensorSufficientStatistics predGradient = (TensorSufficientStatistics) predGradientList.get(index);
      predGradient.increment(assignments.get(i).getPredicateFeatureVector(), count);
    }
  }

  public void incrementActionSufficientStatistics(SufficientStatistics gradient,
      SufficientStatistics currentParameters, Tensor actionFeatures, double count) {
    TensorSufficientStatistics actionGradient = getActionParams(gradient);
    actionGradient.increment(actionFeatures, count);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters) {
    return getParameterDescription(parameters, -1);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) {
    List<SufficientStatistics> eltParams = getElementParams(parameters);
    List<SufficientStatistics> predParams = getPredicateParams(parameters);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < predicateNames.size(); i++) {
      sb.append(predicateNames.get(i));
      sb.append("\n");
      
      TableFactor f = new TableFactor(
          VariableNumMap.singleton(0, eltFeatureVars.get(i).getName(), eltFeatureVars.get(i)),
          ((TensorSufficientStatistics) eltParams.get(i)).get());
      sb.append(f.describeAssignments(f.getMostLikelyAssignments(numFeatures)));
      
      f = new TableFactor(
          VariableNumMap.singleton(0, predFeatureVars.get(i).getName(), predFeatureVars.get(i)),
          ((TensorSufficientStatistics) predParams.get(i)).get());
      sb.append(f.describeAssignments(f.getMostLikelyAssignments(numFeatures)));
    }
    
    sb.append("action parameters:\n");
    TableFactor f = new TableFactor(
        VariableNumMap.singleton(0, actionFeatureVar.getName(), actionFeatureVar),
        getActionParams(parameters).get());
    sb.append(f.describeAssignments(f.getMostLikelyAssignments(numFeatures)));

    return sb.toString();
  }
}
