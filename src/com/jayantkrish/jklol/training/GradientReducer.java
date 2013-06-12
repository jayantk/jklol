package com.jayantkrish.jklol.training;

import com.jayantkrish.jklol.parallel.Reducer.SimpleReducer;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.GradientMapper.GradientEvaluation;

/**
 * Reducer for accumulating gradients from multiple examples.
 * 
 * @author jayantk
 */
public class GradientReducer<M, E> extends SimpleReducer<GradientEvaluation> {
  
  private final LogFunction log;
  private final GradientOracle<M, ? super E> oracle;
  
  public GradientReducer(GradientOracle<M, ? super E> oracle, LogFunction log) {
    this.oracle = oracle;
    this.log = log;
  }
  
  @Override
  public GradientEvaluation getInitialValue() {
    log.startTimer("initialize_gradient");
    SufficientStatistics gradient = oracle.initializeGradient();
    log.stopTimer("initialize_gradient");
    return new GradientEvaluation(gradient, 0.0, 0);
  }

  @Override
  public GradientEvaluation reduce(GradientEvaluation item, GradientEvaluation accumulated) {
    log.startTimer("reduce_gradient");
    accumulated.increment(item);
    log.stopTimer("reduce_gradient");
    return accumulated;
  }
}