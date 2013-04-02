package com.jayantkrish.jklol.boost;

import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.LogFunction;

/**
 * Interface for training a model using boosting. This interface
 * defines methods for computing functional gradients and projecting
 * those gradients on to the space of feasible hypotheses (i.e.,
 * training regressors). Typically, a boosting oracle is an
 * implementation of a specific loss function.
 * 
 * @author jayantk
 * @param <M>
 * @param <E>
 */
public interface BoostingOracle<M, E> {

  FunctionalGradient initializeFunctionalGradient();

  M instantiateModel(SufficientStatisticsEnsemble parameters);

  double accumulateGradient(FunctionalGradient functionalGradient, M model, E example, LogFunction log);

  SufficientStatistics projectGradient(FunctionalGradient functionalGradient);
}
