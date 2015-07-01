package com.jayantkrish.jklol.ccg.lexinduct;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.inference.MarginalCalculator;
import com.jayantkrish.jklol.inference.MarginalCalculator.ZeroProbabilityError;
import com.jayantkrish.jklol.inference.MarginalSet;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.AbstractEmOracle;
import com.jayantkrish.jklol.training.LogFunction;

public class AlignmentEmOracle extends AbstractEmOracle<AlignmentModel, AlignmentExample> {

  private final ParametricAlignmentModel pam;
  private final MarginalCalculator marginalCalculator;
  private final SufficientStatistics smoothing;

  public AlignmentEmOracle(ParametricAlignmentModel pam, MarginalCalculator marginalCalculator,
      SufficientStatistics smoothing, boolean useCfg) {
    this.pam = Preconditions.checkNotNull(pam);
    this.marginalCalculator = Preconditions.checkNotNull(marginalCalculator);
    this.smoothing = Preconditions.checkNotNull(smoothing);
  }

  @Override
  public AlignmentModel instantiateModel(SufficientStatistics parameters) {
    return pam.getModelFromParameters(parameters);
  }

  @Override
  public SufficientStatistics getInitialExpectationAccumulator() {
    return pam.getNewSufficientStatistics();
  }

  @Override
  public SufficientStatistics computeExpectations(AlignmentModel model,
      SufficientStatistics currentParameters, AlignmentExample example, LogFunction log) {
    log.startTimer("e_step/getFactorGraph");
    FactorGraph fg = model.getFactorGraph(example);
    log.stopTimer("e_step/getFactorGraph");

    log.startTimer("e_step/marginals");
    MarginalSet marginals = null;
    try {
      marginals = marginalCalculator.computeMarginals(fg);
    } catch (ZeroProbabilityError e) {
      System.out.println("zero probability: " + example.getTree().getExpression());
      throw e;
    }
    log.stopTimer("e_step/marginals");

    log.startTimer("e_step/compute_expectations");
    SufficientStatistics statistics = pam.getNewSufficientStatistics();
    pam.incrementSufficientStatistics(statistics, currentParameters, marginals, 1.0);
    log.startTimer("e_step/compute_expectations");

    return statistics;
  }

  @Override
  public SufficientStatistics maximizeParameters(SufficientStatistics expectations,
      SufficientStatistics currentParameters, LogFunction log) {
    SufficientStatistics aggregate = pam.getNewSufficientStatistics();
    aggregate.increment(smoothing, 1.0);
    aggregate.increment(expectations, 1.0);

    return aggregate;
  }
}
