package com.jayantkrish.jklol.training;

import com.jayantkrish.jklol.parallel.Reducer.SimpleReducer;
import com.jayantkrish.jklol.training.GradientMapper.GradientEvaluation;

/**
 * Reducer for accumulating gradients from multiple examples.
 * 
 * @author jayantk
 */
public class GradientReducer<M, E> extends SimpleReducer<GradientEvaluation> {
  
  private final LogFunction log;
  private final GradientOracle<M, E> oracle;
  
  public GradientReducer(GradientOracle<M, E> oracle, LogFunction log) {
    this.oracle = oracle;
    this.log = log;
  }
  
  @Override
  public GradientEvaluation getInitialValue() {
    return new GradientEvaluation(oracle.initializeGradient(), 0.0);
  }
  
  @Override
  public GradientEvaluation reduce(GradientEvaluation item, GradientEvaluation accumulated) {
    log.startTimer("accumulate_gradient");
    accumulated.incrementGradient(item.getGradient());
    accumulated.incrementObjectiveValue(item.getObjectiveValue());
    log.stopTimer("accumulate_gradient");
    return accumulated;
  }
}