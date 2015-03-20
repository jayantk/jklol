package com.jayantkrish.jklol.ccg.lexinduct;

import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.inference.MarginalCalculator;
import com.jayantkrish.jklol.inference.MarginalSet;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.EmOracle;
import com.jayantkrish.jklol.training.LogFunction;

public class AlignmentEmOracle implements EmOracle<AlignmentModel, AlignmentExample, SufficientStatistics> {

  private final ParametricAlignmentModel pam;
  private final MarginalCalculator marginalCalculator;
  private final SufficientStatistics smoothing;

  public AlignmentEmOracle(ParametricAlignmentModel pam, MarginalCalculator marginalCalculator,
      SufficientStatistics smoothing) {
    this.pam = Preconditions.checkNotNull(pam);
    this.marginalCalculator = Preconditions.checkNotNull(marginalCalculator);
    this.smoothing = Preconditions.checkNotNull(smoothing);
  }

  @Override
  public AlignmentModel instantiateModel(SufficientStatistics parameters) {
    return pam.getModelFromParameters(parameters);
  }

  @Override
  public SufficientStatistics computeExpectations(AlignmentModel model,
      SufficientStatistics currentParameters, AlignmentExample example, LogFunction log) {
    log.startTimer("e_step/getFactorGraph");
    FactorGraph fg = model.getFactorGraph(example);
    log.stopTimer("e_step/getFactorGraph");

    log.startTimer("e_step/marginals");
    MarginalSet marginals = marginalCalculator.computeMarginals(fg);
    log.stopTimer("e_step/marginals");

    log.startTimer("e_step/compute_expectations");
    SufficientStatistics statistics = pam.getNewSufficientStatistics();
    pam.incrementSufficientStatistics(statistics, currentParameters, marginals, 1.0);
    log.startTimer("e_step/compute_expectations");

    return statistics;
  }

  @Override
  public SufficientStatistics maximizeParameters(List<SufficientStatistics> expectations,
      SufficientStatistics currentParameters, LogFunction log) {

    SufficientStatistics aggregate = pam.getNewSufficientStatistics();
    aggregate.increment(smoothing, 1.0);
    
    for (SufficientStatistics expectation : expectations) {
      aggregate.increment(expectation, 1.0);
    }
    
    return aggregate;
  }
}
