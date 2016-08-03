package com.jayantkrish.jklol.p3;

import java.util.List;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.lisp.AmbEval;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.lisp.SExpression;
import com.jayantkrish.jklol.lisp.inc.ContinuationIncEval;
import com.jayantkrish.jklol.lisp.inc.IncEval;
import com.jayantkrish.jklol.lisp.inc.IncEvalState;
import com.jayantkrish.jklol.lisp.inc.ParametricContinuationIncEval.StateFeatures;
import com.jayantkrish.jklol.lisp.inc.ParametricIncEval;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.util.IndexedList;

public class KbParametricContinuationIncEval implements ParametricIncEval {
  private static final long serialVersionUID = 2L;
  
  private final ParametricKbModel family;
    
  private final ContinuationIncEval eval;  
  
  public KbParametricContinuationIncEval(ParametricKbModel family,
      ContinuationIncEval eval) {
    this.family = Preconditions.checkNotNull(family);
    this.eval = Preconditions.checkNotNull(eval);
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    return family.getNewSufficientStatistics();
  }

  @Override
  public IncEval getModelFromParameters(SufficientStatistics parameters) {
    return new KbContinuationIncEval(eval.getEval(), eval.getInitialEnvironment(),
        eval.getCpsTransform(), eval.getDefs(), family.getModelFromParameters(parameters));
  }

  @Override
  public void incrementSufficientStatistics(SufficientStatistics gradient,
      SufficientStatistics currentParameters, Expression2 lf, IncEvalState state, double count) {
    KbState kbState = (KbState) state.getDiagram();
    family.incrementStateSufficientStatistics(gradient, currentParameters, kbState, count);
    
    // XXX: currently this increment assumes that the model is linear
    Tensor actionFeatures = state.getFeatures(); 
    family.incrementActionSufficientStatistics(gradient, currentParameters, actionFeatures, count);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters) {
    return family.getParameterDescription(parameters, -1);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) {
    return family.getParameterDescription(parameters, numFeatures);
  }

  public static class KbContinuationIncEval extends ContinuationIncEval {
    private final KbModel kbModel;

    public KbContinuationIncEval(AmbEval eval, Environment env,
        Function<Expression2, Expression2> cpsTransform, SExpression defs, KbModel kbModel) {
      super(eval, env, cpsTransform, defs);
      this.kbModel = kbModel;
    }
    
    public KbContinuationIncEval(ContinuationIncEval eval, KbModel kbModel) {
      super(eval.getEval(), eval.getInitialEnvironment(), eval.getCpsTransform(), eval.getDefs());
      this.kbModel = kbModel;
    }

    public KbModel getKbModel() {
      return kbModel;
    }
    
    @Override
    public Tensor getInitialFeatureVector(Object initialDiagram) {
      if (kbModel == null) {
        return null;
      } else {
        return SparseTensor.empty(new int[] {0},
            new int[] {kbModel.getActionFeatureGenerator().getNumberOfFeatures()});
      }
    }

    @Override
    protected void nextState(IncEvalState prev, IncEvalState next, Object continuation,
        Environment env, Object denotation, Object diagram, Object otherArg, LogFunction log) {
      if (kbModel == null) {
        // Allow kbModel to be null to enable evaluation of programs 
        // without scoring them using the same code.
        next.set(continuation, env, denotation, diagram, 1.0, prev.getFeatures());
        return;
      }

      log.startTimer("evaluate_continuation/queue/model");
      KbState state = (KbState) diagram;
      double prob = 1.0;
      IndexedList<String> predicateNames = kbModel.getPredicateNames();
      List<Tensor> classifiers = kbModel.getClassifiers();
      List<Tensor> predClassifiers = kbModel.getPredicateClassifiers();

      // Score the current kb state.
      IndexedList<String> stateFunctionNames = state.getFunctions();
      List<FunctionAssignment> assignments = state.getAssignments();
      List<Tensor> predicateFeatures = state.getPredicateFeatures();
      for (int i : state.getUpdatedFunctionIndexes()) {
        int index = predicateNames.getIndex(stateFunctionNames.get(i));
        Tensor classifier = classifiers.get(index);

        Tensor featureVector = assignments.get(i).getFeatureVector().relabelDimensions(
            classifier.getDimensionNumbers());
        prob *= Math.exp(classifier.innerProduct(featureVector).getByDimKey());
        
        Tensor predClassifier = predClassifiers.get(index);
        Tensor predFeatureVector = predicateFeatures.get(i).relabelDimensions(
            classifier.getDimensionNumbers());
        prob *= Math.exp(predClassifier.innerProduct(predFeatureVector).getByDimKey());
      }

      // Score actions.
      // XXX: this code assumes that the action scoring model is linear. 
      Tensor actionFeaturesSum = prev.getFeatures();
      if (otherArg != null) {
        Tensor actionFeatures = kbModel.generateActionFeatures(new StateFeatures(
            prev, continuation, env, denotation, diagram, otherArg));
        actionFeaturesSum = actionFeaturesSum.elementwiseAddition(actionFeatures);
      }
      prob *= Math.exp(kbModel.getActionClassifier().innerProduct(actionFeaturesSum).getByDimKey());

      log.stopTimer("evaluate_continuation/queue/model");

      next.set(continuation, env, denotation, diagram, prob, actionFeaturesSum);
    }
  }
}
