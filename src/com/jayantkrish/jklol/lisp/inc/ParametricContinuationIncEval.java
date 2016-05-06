package com.jayantkrish.jklol.lisp.inc;

import java.util.Arrays;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.ObjectVariable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.loglinear.ParametricLinearClassifierFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.util.Assignment;

public class ParametricContinuationIncEval implements ParametricIncEval {
  private static final long serialVersionUID = 1L;
  
  private final ParametricFactor family;
  private final VariableNumMap featureVar;
  private final Assignment labelAssignment;

  private final FeatureVectorGenerator<StateFeatures> featureGen;
  
  private final ContinuationIncEval eval;
  
  public ParametricContinuationIncEval(ParametricFactor family, VariableNumMap featureVar,
      Assignment labelAssignment, FeatureVectorGenerator<StateFeatures> featureGen,
      ContinuationIncEval eval) {
    this.family = Preconditions.checkNotNull(family);
    this.featureVar = Preconditions.checkNotNull(featureVar);
    this.labelAssignment = Preconditions.checkNotNull(labelAssignment);
    this.featureGen = Preconditions.checkNotNull(featureGen);
    this.eval = Preconditions.checkNotNull(eval);
  }
  
  public static ParametricContinuationIncEval fromFeatureGenerator(
      FeatureVectorGenerator<StateFeatures> featureGen, ContinuationIncEval eval) {
    // There only needs to be one outcome since there's implicit
    // branching over states.
    ObjectVariable tensorVar = new ObjectVariable(Tensor.class);
    DiscreteVariable outputVar = new DiscreteVariable("true", Arrays.asList(true));

    VariableNumMap input = VariableNumMap.singleton(0, "input", tensorVar);
    VariableNumMap output = VariableNumMap.singleton(1, "output", outputVar);
    
    Assignment labelAssignment = output.outcomeArrayToAssignment(true);
    
    ParametricFactor family = new ParametricLinearClassifierFactor(input, output,
        VariableNumMap.EMPTY, featureGen.getFeatureDictionary(), null, false);

    return new ParametricContinuationIncEval(family, input, labelAssignment, featureGen, eval);
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    return family.getNewSufficientStatistics();
  }

  @Override
  public IncEval getModelFromParameters(SufficientStatistics parameters) {
    Factor classifier = family.getModelFromParameters(parameters);
    return new FeaturizedContinuationIncEval(eval, classifier, featureVar,
        labelAssignment, featureGen);
  }

  @Override
  public void incrementSufficientStatistics(SufficientStatistics gradient,
      SufficientStatistics currentParameters, Expression2 lf, IncEvalState state, double count) {
    if (state.getFeatures() == null) {
      // This should only happen if state is the start state, i.e.,
      // evaluation completely failed.
      return;
    }

    Assignment a = featureVar.outcomeArrayToAssignment(state.getFeatures());
    family.incrementSufficientStatisticsFromAssignment(gradient, currentParameters,
        a.union(labelAssignment), count);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters) {
    return family.getParameterDescription(parameters);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) {
    return family.getParameterDescription(parameters, numFeatures);
  }

  public static class StateFeatures {
    private final IncEvalState prev;
    private final Object continuation;
    private final Environment env;
    private final Object denotation;
    private final Object diagram;
    private final Object otherArg;

    public StateFeatures(IncEvalState prev, Object continuation, Environment env,
        Object denotation, Object diagram, Object otherArg) {
      this.prev = prev;
      this.continuation = continuation;
      this.env = env;
      this.denotation = denotation;
      this.diagram = diagram;
      this.otherArg = otherArg;
    }

    public IncEvalState getPrev() {
      return prev;
    }

    public Object getContinuation() {
      return continuation;
    }

    public Environment getEnv() {
      return env;
    }

    public Object getDenotation() {
      return denotation;
    }

    public Object getDiagram() {
      return diagram;
    }
    
    public Object getOtherArg() {
      return otherArg;
    }    
  }
  
  public static class FeaturizedContinuationIncEval extends ContinuationIncEval {
    private final Factor classifier;
    private final VariableNumMap featureVar;
    private final Assignment labelAssignment;

    private final FeatureVectorGenerator<StateFeatures> featureGen;
    
    public FeaturizedContinuationIncEval(ContinuationIncEval eval,
        Factor classifier, VariableNumMap featureVar, Assignment labelAssignment,
        FeatureVectorGenerator<StateFeatures> featureGen) {
      super(eval.eval, eval.env, eval.cpsTransform, eval.defs);
      
      this.classifier = Preconditions.checkNotNull(classifier);
      this.featureVar = Preconditions.checkNotNull(featureVar);
      this.labelAssignment = Preconditions.checkNotNull(labelAssignment);
      this.featureGen = Preconditions.checkNotNull(featureGen);
    }

    @Override
    protected IncEvalState nextState(IncEvalState prev, Object continuation, Environment env,
        Object denotation, Object diagram, Object otherArg, LogFunction log) {
      log.startTimer("evaluate_continuation/queue/model");
      Tensor featureVector = featureGen.apply(new StateFeatures(
          prev, continuation, env,denotation, diagram, otherArg));
      Assignment a = featureVar.outcomeArrayToAssignment(featureVector);
      // Unnormalized probability of this local decision (not the whole execution). 
      double localProb = classifier.getUnnormalizedProbability(a.union(labelAssignment));
      
      Tensor aggregateFeatureVector = featureVector;
      if (prev.getFeatures() != null) {
        aggregateFeatureVector = prev.getFeatures().elementwiseAddition(featureVector);
      }
      log.stopTimer("evaluate_continuation/queue/model");
      
      return new IncEvalState(continuation, env, denotation, diagram,
          prev.getProb() * localProb, aggregateFeatureVector);
    }
  }
}
