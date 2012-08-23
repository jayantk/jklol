package com.jayantkrish.jklol.training;

import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.evaluation.Example;
import com.jayantkrish.jklol.inference.MarginalCalculator;
import com.jayantkrish.jklol.inference.MarginalSet;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Implementation of {@code EmOracle} that works for Bayes Nets that
 * are represented as {@link ParametricFactorGraph}s.
 * 
 * @author jayantk
 */
public class EmFactorGraphOracle implements EmOracle<DynamicFactorGraph,
    DynamicAssignment, SufficientStatistics> {

  private final ParametricFactorGraph parametricModel;
  private final MarginalCalculator marginalCalculator;
  private final SufficientStatistics smoothing;
  
  public EmFactorGraphOracle(ParametricFactorGraph parametricModel, MarginalCalculator marginalCalculator,
      SufficientStatistics smoothing) {
    this.parametricModel = Preconditions.checkNotNull(parametricModel);
    this.marginalCalculator = Preconditions.checkNotNull(marginalCalculator);
    this.smoothing = Preconditions.checkNotNull(smoothing);
  }

  @Override
  public DynamicFactorGraph instantiateModel(SufficientStatistics parameters) {
    return parametricModel.getFactorGraphFromParameters(parameters);
  }

  @Override
  public SufficientStatistics computeExpectations(DynamicFactorGraph model,
      DynamicAssignment example, LogFunction log) {

    log.startTimer("sufficientStatistics/getFactorGraph");
    FactorGraph factorGraph = model.getFactorGraph(example);
    log.stopTimer("sufficientStatistics/getFactorGraph");

    Assignment observedValues = model.getVariables().toAssignment(example);

    log.startTimer("sufficientStatistics/condition");
    FactorGraph conditionalFactorGraph = factorGraph.conditional(observedValues);
    log.stopTimer("sufficientStatistics/condition");

    log.startTimer("sufficientStatistics/marginals");
    MarginalSet marginals = marginalCalculator.computeMarginals(conditionalFactorGraph);
    log.stopTimer("sufficientStatistics/marginals");

    SufficientStatistics statistics = parametricModel.getNewSufficientStatistics();
    parametricModel.incrementSufficientStatistics(statistics, marginals, 1.0);
    return statistics;
  }

  @Override
  public SufficientStatistics maximizeParameters(
      List<Example<DynamicAssignment, SufficientStatistics>> expectations, 
      SufficientStatistics currentParameters, LogFunction log) {

    SufficientStatistics aggregate = parametricModel.getNewSufficientStatistics();
    aggregate.increment(smoothing, 1.0);
    
    for (Example<DynamicAssignment, SufficientStatistics> expectation : expectations) {
      aggregate.increment(expectation.getOutput(), 1.0);
    }
    
    return aggregate;
  }
}
