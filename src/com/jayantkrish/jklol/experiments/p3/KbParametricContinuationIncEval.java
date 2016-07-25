package com.jayantkrish.jklol.experiments.p3;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.lisp.inc.ContinuationIncEval;
import com.jayantkrish.jklol.lisp.inc.IncEval;
import com.jayantkrish.jklol.lisp.inc.IncEvalState;
import com.jayantkrish.jklol.lisp.inc.ParametricIncEval;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.ListSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.models.parametric.TensorSufficientStatistics;
import com.jayantkrish.jklol.p3.FunctionAssignment;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.util.IndexedList;

public class KbParametricContinuationIncEval implements ParametricIncEval {
  private static final long serialVersionUID = 1L;
  
  private final IndexedList<String> predicateNames;
  private final List<DiscreteVariable> eltFeatureVars;
  private final List<DiscreteVariable> predFeatureVars;
    
  private final ContinuationIncEval eval;  
  
  public KbParametricContinuationIncEval(IndexedList<String> predicateNames,
      List<DiscreteVariable> eltFeatureVars, List<DiscreteVariable> predFeatureVars,
      ContinuationIncEval eval) {
    this.predicateNames = Preconditions.checkNotNull(predicateNames);
    this.eltFeatureVars = Preconditions.checkNotNull(eltFeatureVars);
    this.predFeatureVars = Preconditions.checkNotNull(predFeatureVars);

    Preconditions.checkArgument(predicateNames.size() == eltFeatureVars.size());
    Preconditions.checkArgument(predicateNames.size() == predFeatureVars.size());

    this.eval = Preconditions.checkNotNull(eval);
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    List<SufficientStatistics> statistics = Lists.newArrayList();
    for (int i = 0; i < predicateNames.size(); i++) {
      DiscreteVariable featureVar = eltFeatureVars.get(i);
      statistics.add(TensorSufficientStatistics.createDense(
          VariableNumMap.singleton(0, featureVar.getName(), featureVar),
          new DenseTensorBuilder(new int[] {0}, new int[] {featureVar.numValues()})));
    }

    List<SufficientStatistics> predStatistics = Lists.newArrayList();
    for (int i = 0; i < predicateNames.size(); i++) {
      DiscreteVariable featureVar = predFeatureVars.get(i);
      predStatistics.add(TensorSufficientStatistics.createDense(
          VariableNumMap.singleton(0, featureVar.getName(), featureVar),
          new DenseTensorBuilder(new int[] {0}, new int[] {featureVar.numValues()})));
    }

    return new ListSufficientStatistics(Arrays.asList("element", "predicate"),
        Arrays.asList(new ListSufficientStatistics(predicateNames, statistics),
            new ListSufficientStatistics(predicateNames, predStatistics)));
  }
  
  private static List<SufficientStatistics> getElementParams(SufficientStatistics params) {
    List<SufficientStatistics> paramList = params.coerceToList().getStatistics();
    return paramList.get(0).coerceToList().getStatistics();
  }

  private static List<SufficientStatistics> getPredicateParams(SufficientStatistics params) {
    List<SufficientStatistics> paramList = params.coerceToList().getStatistics();
    return paramList.get(1).coerceToList().getStatistics();
  }

  @Override
  public IncEval getModelFromParameters(SufficientStatistics parameters) {
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

    KbModel kbModel = new KbModel(predicateNames, classifiers, predClassifiers);
    return new KbContinuationIncEval(eval, kbModel);
  }

  @Override
  public void incrementSufficientStatistics(SufficientStatistics gradient,
      SufficientStatistics currentParameters, Expression2 lf, IncEvalState state, double count) {
    KbState kbState = (KbState) state.getDiagram();
    
    List<SufficientStatistics> eltGradientList = getElementParams(gradient);
    List<SufficientStatistics> predGradientList = getPredicateParams(gradient);

    IndexedList<String> stateFunctionNames = kbState.getFunctions();
    List<FunctionAssignment> assignments = kbState.getAssignments();
    List<Tensor> predicateFeatures = kbState.getPredicateFeatures();
    for (int i : kbState.getUpdatedFunctionIndexes()) {
      int index = predicateNames.getIndex(stateFunctionNames.get(i));
      
      TensorSufficientStatistics eltGradient = (TensorSufficientStatistics) eltGradientList.get(index);
      eltGradient.increment(assignments.get(i).getFeatureVector(), count);
      
      TensorSufficientStatistics predGradient = (TensorSufficientStatistics) predGradientList.get(index);
      predGradient.increment(predicateFeatures.get(i), count);
    }
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
    return sb.toString();
  }

  public static class KbContinuationIncEval extends ContinuationIncEval {
    private final KbModel kbModel;

    public KbContinuationIncEval(ContinuationIncEval eval,
        KbModel kbModel) {
      super(eval.getEval(), eval.getInitialEnvironment(), eval.getCpsTransform(), eval.getDefs());
      this.kbModel = Preconditions.checkNotNull(kbModel);
    }

    public KbModel getKbModel() {
      return kbModel;
    }

    @Override
    protected IncEvalState nextState(IncEvalState prev, Object continuation, Environment env,
        Object denotation, Object diagram, Object otherArg, LogFunction log) {
      
      KbState state = (KbState) diagram;
      
      log.startTimer("evaluate_continuation/queue/model");
      double prob = 1.0;
      IndexedList<String> predicateNames = kbModel.getPredicateNames();
      List<Tensor> classifiers = kbModel.getClassifiers();
      List<Tensor> predClassifiers = kbModel.getPredicateClassifiers();
      /*
      System.out.println(state.getCategories());
      System.out.println(state.getRelations());
      System.out.println(predicateNames);
      */
      IndexedList<String> stateFunctionNames = state.getFunctions();
      List<FunctionAssignment> assignments = state.getAssignments();
      List<Tensor> predicateFeatures = state.getPredicateFeatures();
      for (int i : state.getUpdatedFunctionIndexes()) {
        int index = predicateNames.getIndex(stateFunctionNames.get(i));
        Tensor classifier = classifiers.get(index);

        Tensor featureVector = assignments.get(i).getFeatureVector().relabelDimensions(
            classifier.getDimensionNumbers());
        prob *= Math.exp(classifier.innerProduct(featureVector).getByDimKey(new int[0]));
        
        Tensor predClassifier = predClassifiers.get(index);
        Tensor predFeatureVector = predicateFeatures.get(i).relabelDimensions(
            classifier.getDimensionNumbers());
        prob *= Math.exp(predClassifier.innerProduct(predFeatureVector).getByDimKey(new int[0]));
      }

      log.stopTimer("evaluate_continuation/queue/model");

      return new IncEvalState(continuation, env, denotation, diagram, prob, null);
    }
  }
}
