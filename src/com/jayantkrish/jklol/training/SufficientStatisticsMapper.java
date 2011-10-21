package com.jayantkrish.jklol.training;

import com.jayantkrish.jklol.inference.MarginalCalculator;
import com.jayantkrish.jklol.inference.MarginalSet;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.parallel.Mapper;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Half of a map-reduce pipeline for computing the sufficient statistics of a
 * collection of {@code Assignment}s. This class computes marginals and
 * sufficient statistics for a single example. The second half of the pipeline
 * is {@link SufficientStatisticsReducer}.
 * 
 * @author jayantk
 */
public class SufficientStatisticsMapper extends Mapper<Assignment, SufficientStatisticsBatch> {

  private final FactorGraph factorGraph;
  private final ParametricFactorGraph parametricFactorGraph;
  private final MarginalCalculator marginalCalculator;
  private final LogFunction logFn;

  public SufficientStatisticsMapper(FactorGraph factorGraph,
      ParametricFactorGraph parametricFactorGraph, MarginalCalculator marginalCalculator,
      LogFunction logFn) {
    this.factorGraph = factorGraph;
    this.parametricFactorGraph = parametricFactorGraph;
    this.marginalCalculator = marginalCalculator;
    this.logFn = logFn;
  }

  @Override
  public SufficientStatisticsBatch map(Assignment item) {
    logFn.log(item, factorGraph);
    MarginalSet marginals = marginalCalculator.computeMarginals(factorGraph, item);

    return new SufficientStatisticsBatch(
        parametricFactorGraph.computeSufficientStatistics(marginals, 1.0),
        Math.log(marginals.getPartitionFunction()), 1);
  }
}
