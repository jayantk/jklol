package com.jayantkrish.jklol.training;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.inference.MarginalSet;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
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
  private final SufficientStatistics currentParameters;

  public SufficientStatisticsReducer(ParametricFactorGraph parametricFactorGraph,
      SufficientStatistics currentParameters) {
    this.parametricFactorGraph = Preconditions.checkNotNull(parametricFactorGraph);
    this.currentParameters = Preconditions.checkNotNull(currentParameters);
  }

  @Override
  public SufficientStatisticsBatch getInitialValue() {
    return new SufficientStatisticsBatch(parametricFactorGraph.getNewSufficientStatistics(), 0.0, 0);
  }

  @Override
  public SufficientStatisticsBatch reduce(MarginalSet item, SufficientStatisticsBatch accumulator) {
    parametricFactorGraph.incrementSufficientStatistics(accumulator.getStatistics(),
        currentParameters, item, 1.0);
    accumulator.incrementLogLikelihood(item.getLogPartitionFunction());
    accumulator.incrementNumExamples(1);
    return accumulator;
  }
  
  public SufficientStatisticsBatch combine(SufficientStatisticsBatch item, SufficientStatisticsBatch accumulated) {
    accumulated.increment(item);
    return accumulated;
  }
}
