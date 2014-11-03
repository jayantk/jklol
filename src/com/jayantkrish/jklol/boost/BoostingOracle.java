package com.jayantkrish.jklol.boost;

import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.LogFunction;

/**
 * Interface for training a model using boosting. This interface
 * defines methods for computing functional gradients and projecting
 * those gradients on to the space of feasible hypotheses (i.e.,
 * training regressors). A boosting oracle represents a specific 
 * loss function to optimize.
 * <p>
 * {@code BoostingOracle} is generically defined for families of
 * models and training examples. Models {@code M} are included
 * to speed up inference, as some computation can be done once 
 * then shared by all examples in the data set.
 * 
 * @author jayantk
 * @param <M> model type
 * @param <E> training example type
 */
public interface BoostingOracle<M, E> {

  /**
   * Gets a new, empty functional gradient.
   * 
   * @return a new, empty functional gradient.
   */
  FunctionalGradient initializeFunctionalGradient();

  /**
   * Gets a model given an ensemble of model parameters. This method
   * helps reduce computation by performing some shared operations 
   * required for model inference on all examples. For example,
   * if the returned model is a factor graph, some factor ensembles
   * can be combined by this method.   
   *  
   * @param parameters
   * @return a model (caching out some computation)
   */
  M instantiateModel(SufficientStatisticsEnsemble parameters);

  /**
   * Adds functional gradient information for {@code example} to 
   * {@code functionalGradient}.
   * 
   * @param functionalGradient
   * @param model
   * @param example
   * @param log
   * @return objective value (negative loss) at this example. 
   */
  double accumulateGradient(FunctionalGradient functionalGradient, M model, E example, LogFunction log);

  /**
   * Trains regression functions to approximate {@code functionalGradient} 
   * with an achievable hypothesis. 
   *  
   * @param functionalGradient
   * @return parameters of the trained regressors.
   */
  SufficientStatistics projectGradient(FunctionalGradient functionalGradient);
}
