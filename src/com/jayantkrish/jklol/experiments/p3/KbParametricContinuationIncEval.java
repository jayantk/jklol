package com.jayantkrish.jklol.experiments.p3;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.lisp.inc.ContinuationIncEval;
import com.jayantkrish.jklol.lisp.inc.IncEval;
import com.jayantkrish.jklol.lisp.inc.IncEval.IncEvalState;
import com.jayantkrish.jklol.lisp.inc.ParametricIncEval;
import com.jayantkrish.jklol.models.LinearClassifierFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.loglinear.ParametricLinearClassifierFactor;
import com.jayantkrish.jklol.models.parametric.ListSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IndexedList;

public class KbParametricContinuationIncEval implements ParametricIncEval {
  private static final long serialVersionUID = 1L;
  
  private final IndexedList<String> predicateNames;
  private final List<ParametricLinearClassifierFactor> families;
  private final List<VariableNumMap> featureVars;
  private final List<Assignment> labelAssignments;

  private final KbFeatureGenerator featureGenerator;
  
  private final ContinuationIncEval eval;
  
  public KbParametricContinuationIncEval(IndexedList<String> predicateNames,
      List<ParametricLinearClassifierFactor> families, List<VariableNumMap> featureVars,
      List<Assignment> labelAssignments, KbFeatureGenerator featureGenerator,
      ContinuationIncEval eval) {
    this.predicateNames = Preconditions.checkNotNull(predicateNames);
    this.families = Preconditions.checkNotNull(families);
    this.featureVars = Preconditions.checkNotNull(featureVars);
    this.labelAssignments = Preconditions.checkNotNull(labelAssignments);
    
    Preconditions.checkArgument(predicateNames.size() == families.size());
    Preconditions.checkArgument(predicateNames.size() == featureVars.size());
    Preconditions.checkArgument(predicateNames.size() == labelAssignments.size());
    
    this.featureGenerator = Preconditions.checkNotNull(featureGenerator);
    this.eval = Preconditions.checkNotNull(eval);
  }
  
  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    List<SufficientStatistics> statistics = Lists.newArrayList();
    for (int i = 0; i < families.size(); i++) {
      statistics.add(families.get(i).getNewSufficientStatistics());
    }

    return new ListSufficientStatistics(predicateNames, statistics);
  }

  @Override
  public IncEval getModelFromParameters(SufficientStatistics parameters) {
    List<SufficientStatistics> parameterList = parameters.coerceToList().getStatistics();
    List<Tensor> classifiers = Lists.newArrayList();
    for (int i = 0; i < predicateNames.size(); i++) {
      LinearClassifierFactor classifier = families.get(i).getModelFromParameters(parameterList.get(i));
      classifiers.add(classifier.getFeatureWeightsForClass(labelAssignments.get(i)));
    }
    KbModel kbModel = new KbModel(predicateNames, classifiers, featureGenerator);
    
    return new KbContinuationIncEval(eval, kbModel);
  }

  @Override
  public void incrementSufficientStatistics(SufficientStatistics gradient,
      SufficientStatistics currentParameters, Expression2 lf, IncEvalState state, double count) {
    KbState kbState = (KbState) state.getDiagram();
    KbFeatures features = featureGenerator.apply(kbState);
    
    List<SufficientStatistics> gradientList = gradient.coerceToList().getStatistics();
    List<SufficientStatistics> parameterList = currentParameters.coerceToList().getStatistics();
    
    for (String predicate : features.getPredicates()) {
      int index = predicateNames.getIndex(predicate);
      ParametricFactor family = families.get(index);
      VariableNumMap featureVar = featureVars.get(index);
      Assignment labelAssignment = labelAssignments.get(index);

      Assignment a = featureVar.outcomeArrayToAssignment(features.getFeatureVector(predicate));
      family.incrementSufficientStatisticsFromAssignment(gradientList.get(index),
          parameterList.get(index), a.union(labelAssignment), count);
    }
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters) {
    return getParameterDescription(parameters, -1);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) {
    List<SufficientStatistics> parameterList = parameters.coerceToList().getStatistics();
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < families.size(); i++) {
      sb.append(predicateNames.get(i));
      sb.append(families.get(i).getParameterDescription(parameterList.get(i), numFeatures));
    }
    return sb.toString();
  }
  
  public static class KbContinuationIncEval extends ContinuationIncEval {
    private final KbModel kbModel;

    public KbContinuationIncEval(ContinuationIncEval eval,
        KbModel kbModel) {
      super(eval.getEval(), eval.getEnv(), eval.getSimplifier(),
          eval.getDefs(), eval.getLfConversion());

      this.kbModel = Preconditions.checkNotNull(kbModel);
    }

    public KbModel getKbModel() {
      return kbModel;
    }

    @Override
    protected IncEvalState nextState(IncEvalState prev, Object continuation, Environment env,
        Object denotation, Object diagram, Object otherArg, LogFunction log) {
      
      log.startTimer("evaluate_continuation/queue/feature_gen");
      KbState state = (KbState) diagram;
      KbFeatures features = kbModel.getFeatureGenerator().apply(state);
      log.stopTimer("evaluate_continuation/queue/feature_gen");
      
      log.startTimer("evaluate_continuation/queue/model");
      double prob = 1.0;
      IndexedList<String> predicateNames = kbModel.getPredicateNames();
      /*
      System.out.println(state.getCategories());
      System.out.println(state.getRelations());
      System.out.println(predicateNames);
      */
      List<Tensor> classifiers = kbModel.getClassifiers();
      for (String predicate : features.getPredicates()) {
        int index = predicateNames.getIndex(predicate);
        Tensor classifier = classifiers.get(index);

        Tensor featureVector = features.getFeatureVector(predicate)
            .relabelDimensions(classifier.getDimensionNumbers());
        prob *= Math.exp(classifier.innerProduct(featureVector).getByDimKey(new int[0]));
      }
      log.stopTimer("evaluate_continuation/queue/model");

      return new IncEvalState(continuation, env, denotation, diagram, prob, null);
    }
  }
}
