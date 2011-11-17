package com.jayantkrish.jklol.training;

import com.jayantkrish.jklol.inference.MarginalCalculator;
import com.jayantkrish.jklol.inference.MarginalSet;
import com.jayantkrish.jklol.models.FactorGraph;
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
public class SufficientStatisticsMapper extends Mapper<Assignment, MarginalSet> {

  private final FactorGraph factorGraph;
  private final MarginalCalculator marginalCalculator;
  private final LogFunction logFn;

  public SufficientStatisticsMapper(FactorGraph factorGraph,
      MarginalCalculator marginalCalculator, LogFunction logFn) {
    this.factorGraph = factorGraph;
    this.marginalCalculator = marginalCalculator;
    this.logFn = logFn;
  }

  @Override
  public MarginalSet map(Assignment item) {
    logFn.log(item, factorGraph);
    FactorGraph conditionalFactorGraph = factorGraph.conditional(item);
    return marginalCalculator.computeMarginals(conditionalFactorGraph)
        .addConditionalVariables(item);
  }
}
