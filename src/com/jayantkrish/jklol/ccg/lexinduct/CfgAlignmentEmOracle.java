package com.jayantkrish.jklol.ccg.lexinduct;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.cfg.CfgExpectation;
import com.jayantkrish.jklol.cfg.CfgParseChart;
import com.jayantkrish.jklol.cfg.CfgParser;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.ListSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.training.DefaultLogFunction;
import com.jayantkrish.jklol.training.EmOracle;
import com.jayantkrish.jklol.training.FactorLoglikelihoodOracle;
import com.jayantkrish.jklol.training.Lbfgs;
import com.jayantkrish.jklol.training.LbfgsConvergenceError;
import com.jayantkrish.jklol.training.LogFunction;

public class CfgAlignmentEmOracle implements EmOracle<CfgAlignmentModel, AlignmentExample, CfgExpectation, CfgExpectation>{

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
  public CfgExpectation getInitialExpectationAccumulator() {
    TableFactorBuilder ruleBuilder = new TableFactorBuilder(
        pam.getRuleFactor().getVars(), DenseTensorBuilder.getFactory());
    TableFactorBuilder nonterminalBuilder = new TableFactorBuilder(
        pam.getNonterminalFactor().getVars(), SparseTensorBuilder.getFactory());
    TableFactorBuilder terminalBuilder = new TableFactorBuilder(
        pam.getTerminalFactor().getVars(), SparseTensorBuilder.getFactory());
    return new CfgExpectation(ruleBuilder, nonterminalBuilder, terminalBuilder);
  }

  @Override
  public CfgExpectation computeExpectations(CfgAlignmentModel model,
      SufficientStatistics currentParameters, AlignmentExample example,
      CfgExpectation accumulator, LogFunction log) {
    log.startTimer("e_step/getCfg");
    CfgParser parser = model.getCfgParser(example);
    log.stopTimer("e_step/getCfg");

    log.startTimer("e_step/marginals");
    Factor rootFactor = model.getRootFactor(example.getTree(), parser.getParentVariable());
    CfgParseChart chart = parser.parseMarginal(example.getWords(), rootFactor, true);
    log.stopTimer("e_step/marginals");

    log.startTimer("e_step/compute_expectations");
    pam.incrementExpectations(accumulator, chart, 1.0);
    log.stopTimer("e_step/compute_expectations");
    
    return accumulator;
  }

  @Override
  public SufficientStatistics maximizeParameters(CfgExpectation expectations,
      SufficientStatistics currentParameters, LogFunction log) {
    if (pam.isLoglinear()) {
      List<SufficientStatistics> paramList = currentParameters.coerceToList().getStatistics();
      
      DiscreteFactor ruleTarget = expectations.getRuleBuilder().build();
      ParametricFactor ruleFamily = pam.getRuleFactor();
      SufficientStatistics ruleParameters = trainFamily(ruleFamily, ruleTarget, pam.getNonterminalVar(),
          paramList.get(0));

      DiscreteFactor nonterminalTarget = expectations.getNonterminalBuilder().build();
      ParametricFactor nonterminalFamily = pam.getNonterminalFactor();
      SufficientStatistics nonterminalParameters = trainFamily(nonterminalFamily, nonterminalTarget,
          pam.getNonterminalVar().union(pam.getRuleVar()), paramList.get(1));

      DiscreteFactor terminalTarget = expectations.getTerminalBuilder().build();
      ParametricFactor terminalFamily = pam.getTerminalFactor();
      SufficientStatistics terminalParameters = trainFamily(terminalFamily, terminalTarget,
          pam.getNonterminalVar().union(pam.getRuleVar()), paramList.get(2));

      return new ListSufficientStatistics(Arrays.asList("rules", "nonterminals", "terminals"),
          Arrays.asList(ruleParameters, nonterminalParameters, terminalParameters));
    } else {
      SufficientStatistics aggregate = pam.getNewSufficientStatistics();
      aggregate.increment(smoothing, 1.0);
      pam.incrementSufficientStatistics(expectations, aggregate, currentParameters, 1.0);
      return aggregate;
    }
  }
  
  private static SufficientStatistics trainFamily(ParametricFactor family, Factor target,
      VariableNumMap conditionalVars, SufficientStatistics currentParameters) {
    int numIterations = 1000;
    // TODO: fix regularization?
    SufficientStatistics optimizedParameters = null;
    try {
      Lbfgs lbfgs = new Lbfgs(numIterations, 10, 1e-8, new DefaultLogFunction(numIterations - 1, false));
      FactorLoglikelihoodOracle oracle = new FactorLoglikelihoodOracle(family, target, conditionalVars);
      optimizedParameters = lbfgs.train(oracle, currentParameters.duplicate(), Arrays.asList((Void) null));
    } catch (LbfgsConvergenceError e) {
      optimizedParameters = e.getFinalParameters();
    }
    return optimizedParameters;
  }

  @Override
  public CfgExpectation combineAccumulators(CfgExpectation accumulator1,
      CfgExpectation accumulator2) {
    accumulator1.increment(accumulator2);
    return accumulator1;
  }
}
