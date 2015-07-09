package com.jayantkrish.jklol.ccg.lexinduct;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.cfg.CfgParseChart;
import com.jayantkrish.jklol.cfg.CfgParser;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.AbstractEmOracle;
import com.jayantkrish.jklol.training.LogFunction;

public class CfgAlignmentEmOracle extends AbstractEmOracle<CfgAlignmentModel, AlignmentExample>{

  private final ParametricCfgAlignmentModel pam;
  private final SufficientStatistics smoothing;
  
  public CfgAlignmentEmOracle(ParametricCfgAlignmentModel pam, SufficientStatistics smoothing) {
    this.pam = Preconditions.checkNotNull(pam);
    this.smoothing = Preconditions.checkNotNull(smoothing);
  }

  @Override
  public CfgAlignmentModel instantiateModel(SufficientStatistics parameters) {
    return pam.getModelFromParameters(parameters);
  }
  
  @Override
  public SufficientStatistics getInitialExpectationAccumulator() {
    return pam.getNewSufficientStatistics();
  }

  @Override
  public SufficientStatistics computeExpectations(CfgAlignmentModel model,
      SufficientStatistics currentParameters, AlignmentExample example,
      SufficientStatistics accumulator, LogFunction log) {
    log.startTimer("e_step/getCfg");
    CfgParser parser = model.getCfgParser(example);
    log.stopTimer("e_step/getCfg");

    log.startTimer("e_step/marginals");
    Factor rootFactor = model.getRootFactor(example.getTree(), parser.getParentVariable());
    CfgParseChart chart = parser.parseMarginal(example.getWords(), rootFactor, true);
    log.stopTimer("e_step/marginals");

    log.startTimer("e_step/compute_expectations");
    pam.incrementSufficientStatistics(accumulator, currentParameters, chart, 1.0);
    log.stopTimer("e_step/compute_expectations");

    return accumulator;
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
