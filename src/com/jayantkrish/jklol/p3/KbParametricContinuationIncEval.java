package com.jayantkrish.jklol.p3;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.lisp.inc.ContinuationIncEval;
import com.jayantkrish.jklol.lisp.inc.IncEval;
import com.jayantkrish.jklol.lisp.inc.IncEvalState;
import com.jayantkrish.jklol.lisp.inc.ParametricIncEval;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.Tensor;

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
}
