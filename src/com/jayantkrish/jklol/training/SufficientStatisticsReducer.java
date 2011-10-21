package com.jayantkrish.jklol.training;

import com.jayantkrish.jklol.inference.MarginalSet;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.parallel.Reducer;

/**
 * Second half of a map-reduce pipeline for computing the sufficient statistics
 * of a batch of examples. This reducer adds up the sufficient statistics each
 * example.
 * 
 * @author jayantk
 */
public class SufficientStatisticsReducer implements Reducer<MarginalSet, SufficientStatisticsBatch> {

  private final ParametricFactorGraph parametricFactorGraph;

  public SufficientStatisticsReducer(ParametricFactorGraph parametricFactorGraph) {
    this.parametricFactorGraph = parametricFactorGraph;
  }

  @Override
  public SufficientStatisticsBatch getInitialValue() {
    return new SufficientStatisticsBatch(parametricFactorGraph.getNewSufficientStatistics(), 0.0, 0);
  }

  @Override
  public SufficientStatisticsBatch reduce(MarginalSet item, SufficientStatisticsBatch accumulator) {
    parametricFactorGraph.incrementSufficientStatistics(accumulator.getStatistics(), item, 1.0);
    accumulator.incrementLogLikelihood(Math.log(item.getPartitionFunction()));
    accumulator.incrementNumExamples(1);
    return accumulator;
  }
  
  public SufficientStatisticsBatch combine(SufficientStatisticsBatch item, SufficientStatisticsBatch accumulated) {
    accumulated.increment(item);
    return accumulated;
  }
}
