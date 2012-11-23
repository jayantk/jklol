package com.jayantkrish.jklol.training;

import java.util.List;

import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

/**
 * Oracle for the steps of the {@link ExpectationMaximization} algorithm.
 * 
 * @author jayantk
 * @param <M> instantiated model type
 * @param <E> example type
 * @param <O> expectation type
 */
public interface EmOracle<M, E, O> {

  /**
   * Instantiates any sort of intermediate data structure that may assist in
   * performing inference. For example, this could construct a
   * {@code FactorGraph} for {@code parameters}.
   * <p>
   * If no such initialization is necessary, simply return {@code parameters}.
   * 
   * @param parameters
   */
  public M instantiateModel(SufficientStatistics parameters);

  /**
   * E-step of the Expectation-Maximization algorithm.
   * 
   * @param model
   * @param example
   * @return
   */
  public O computeExpectations(M model, E example, LogFunction log);

  /**
   * M-step of the Expectation-Maximization algorithm. Re-estimates parameters
   * from the model expectations computed in the E-step.
   * <p>
   * This method may mutate and return {@code currentParameters} to avoid
   * unnecessary copying.
   * 
   * @param expectations
   * @param currentParameters
   * @return
   */
  public SufficientStatistics maximizeParameters(List<O> expectations,
      SufficientStatistics currentParameters, LogFunction log);
}
