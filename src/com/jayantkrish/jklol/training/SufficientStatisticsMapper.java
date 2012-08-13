package com.jayantkrish.jklol.training;

import com.jayantkrish.jklol.inference.MarginalCalculator;
import com.jayantkrish.jklol.inference.MarginalSet;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.parallel.Mapper;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Half of a map-reduce pipeline for computing the sufficient statistics of a
 * collection of training data, represented as pairs of
 * {@code DynamicAssignment}s. This class computes marginals and sufficient
 * statistics for a single example. The second half of the pipeline is
 * {@link SufficientStatisticsReducer}.
 * 
 * @author jayantk
 */
public class SufficientStatisticsMapper extends Mapper<DynamicAssignment, MarginalSet> {

  private final DynamicFactorGraph dynamicFactorGraph;
  private final MarginalCalculator marginalCalculator;
  private final LogFunction logFn;

  public SufficientStatisticsMapper(DynamicFactorGraph dynamicFactorGraph,
      MarginalCalculator marginalCalculator, LogFunction logFn) {
    this.dynamicFactorGraph = dynamicFactorGraph;
    this.marginalCalculator = marginalCalculator;
    this.logFn = logFn;
  }

  @Override
  public MarginalSet map(DynamicAssignment item) {
    logFn.startTimer("sufficientStatistics/getFactorGraph");
    FactorGraph factorGraph = dynamicFactorGraph.getFactorGraph(item);
    logFn.stopTimer("sufficientStatistics/getFactorGraph");

    Assignment assignment = dynamicFactorGraph.getVariables().toAssignment(item);
    // logFn.log(assignment, factorGraph);

    logFn.startTimer("sufficientStatistics/condition");
    FactorGraph conditionalFactorGraph = factorGraph.conditional(assignment);
    logFn.stopTimer("sufficientStatistics/condition");

    logFn.startTimer("sufficientStatistics/marginals");
    MarginalSet marginals = marginalCalculator.computeMarginals(conditionalFactorGraph);
    logFn.stopTimer("sufficientStatistics/marginals");
    return marginals;
  }
}
