package com.jayantkrish.jklol.training;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.inference.MarginalCalculator.ZeroProbabilityError;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.parallel.Reducer;

/**
 * Reducer for accumulating gradients from multiple examples.
 * 
 * @author jayantk
 */
public class GradientReducer<M, E> implements Reducer<E, GradientEvaluation> {

  private final M instantiatedModel;
  private final GradientOracle<M, ? super E> oracle;

  private final LogFunction log;

  public GradientReducer(M instantiatedModel, GradientOracle<M, ? super E> oracle,
      LogFunction log) {
    this.instantiatedModel = Preconditions.checkNotNull(instantiatedModel);
    this.oracle = Preconditions.checkNotNull(oracle);
    this.log = log;
  }
  
  @Override
  public GradientEvaluation getInitialValue() {
    log.startTimer("mr_gradient_initialize");
    SufficientStatistics gradient = oracle.initializeGradient();
    log.stopTimer("mr_gradient_initialize");
    return new GradientEvaluation(gradient, 0.0, 0);
  }

  @Override
  public GradientEvaluation reduce(E item, GradientEvaluation accumulated) {
    log.startTimer("mr_gradient_map");
    double objective = 0.0;
    int searchErrors = 0;
    try {
      objective += oracle.accumulateGradient(accumulated.getGradient(), 
          instantiatedModel, item, log);
    } catch (ZeroProbabilityError e) {
      // Ignore the example, returning the zero vector.
      searchErrors = 1;
    }
    accumulated.incrementSearchErrors(searchErrors);
    accumulated.incrementObjectiveValue(objective);
    log.stopTimer("mr_gradient_map");
    return accumulated;
  }

  @Override
  public GradientEvaluation combine(GradientEvaluation other, GradientEvaluation accumulated) {
    accumulated.increment(other);
    return accumulated;
  }
}