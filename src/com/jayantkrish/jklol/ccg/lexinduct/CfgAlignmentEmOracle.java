package com.jayantkrish.jklol.ccg.lexinduct;

import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.cfg.CfgParseChart;
import com.jayantkrish.jklol.cfg.CfgParser;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.EmOracle;
import com.jayantkrish.jklol.training.LogFunction;

public class CfgAlignmentEmOracle implements EmOracle<CfgAlignmentModel, AlignmentExample, SufficientStatistics>{

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
  public SufficientStatistics computeExpectations(CfgAlignmentModel model,
      SufficientStatistics currentParameters, AlignmentExample example, LogFunction log) {
    log.startTimer("e_step/getCfg");
    CfgParser parser = model.getCfgParser(example);
    log.stopTimer("e_step/getCfg");

    log.startTimer("e_step/marginals");
    CfgParseChart chart = parser.parseMarginal(example.getWords(), example.getTree().getExpressionNode(), true);
    log.stopTimer("e_step/marginals");

    log.startTimer("e_step/compute_expectations");
    SufficientStatistics statistics = pam.getNewSufficientStatistics();
    pam.incrementSufficientStatistics(statistics, currentParameters, chart, 1.0);
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
