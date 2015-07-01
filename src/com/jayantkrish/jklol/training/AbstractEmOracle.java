package com.jayantkrish.jklol.training;

import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

/**
 * Abstract implementation of {@code EmOracle} where the
 * expectation and aggregation types are both
 * {@code SufficientStatistics}. 
 * 
 * @author jayantk
 *
 */
public abstract class AbstractEmOracle<M, E> implements
EmOracle<M, E, SufficientStatistics, SufficientStatistics> {

  @Override
  public void accumulateExpectation(SufficientStatistics expectation,
      SufficientStatistics accumulator) {
    accumulator.increment(expectation, 1.0);
  }

  @Override
  public SufficientStatistics combineAccumulators(
      SufficientStatistics accumulator1, SufficientStatistics accumulator2) {
    accumulator2.increment(accumulator1, 1.0);
    return accumulator2;
  }
}
