package com.jayantkrish.jklol.training;

import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

/**
 * A procedure for computing first-order (i.e., gradient) information for an optimization
 * objective. This class represents a data-dependent objective function (e.g., loglikelihood),
 * where the objective is a sum over a set of training examples.
 * <p>
 *
 * For computational reasons, the oracle may cache out a model for a given set of parameters using
 * {@link #instantiateModel}. (A typical choice is a {@code FactorGraph} from a {@code
 * ParametricFactorGraph}. This model may be instantiated fewer times than {@link
 * #accumulateGradient} is invoked, saving computation.
 * <p>
 *
 * {@code GradientOracle}s always represent maximization problems. Optimization steps are taken in
 * the direction of computed gradients.
 *
 * @param <M> model type
 * @param <E> training example type
 */
public interface GradientOracle<M, E> {

  /**
   * Returns the all-zero gradient vector.
   *
   * @return
   */
  public SufficientStatistics initializeGradient();

  /**
   * Instantiates any sort of intermediate data structure that may 
   * assist in computing the gradient. For example, this could 
   * construct a {@code FactorGraph} for {@code parameters}.
   * <p>
   * If no such initialization is necessary, simply return 
   * {@code parameters}.
   *
   * @param parameters
   */
  public M instantiateModel(SufficientStatistics parameters);

  /**
   * Computes the an estimate of the objective's gradient at (the
   * parameters corresponding to) {@code instantiatedModel}. The gradient
   * estimate is derived from the portion of objective that depends on
   * {@code example}. {@code gradient} is incremented with the computed 
   * value, and the method returns the current objective value.
   *
   * @param gradient
   * @param currentParameters
   * @param instantiatedModel
   * @param example
   * @param log stores time statistics, etc., about the optimization  
   * @return objective value evaluated at {@code example}.
   */
  public double accumulateGradient(SufficientStatistics gradient,
      SufficientStatistics currentParameters, M instantiatedModel, E example, LogFunction log);
}