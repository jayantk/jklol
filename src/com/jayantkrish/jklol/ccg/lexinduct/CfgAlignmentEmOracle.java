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
import com.jayantkrish.jklol.training.EmOracle;
import com.jayantkrish.jklol.training.FactorLoglikelihoodOracle;
import com.jayantkrish.jklol.training.GradientOptimizer;
import com.jayantkrish.jklol.training.LbfgsConvergenceError;
import com.jayantkrish.jklol.training.LogFunction;

public class CfgAlignmentEmOracle implements EmOracle<CfgAlignmentModel, AlignmentExample, CfgExpectation, CfgExpectation>{

  private final ParametricCfgAlignmentModel pam;

  // Smoothing for estimating parameters of non-loglinear factors.
  private final SufficientStatistics smoothing;
  
  // Optimizer for estimating the parameters of any loglinear models
  // embedded in the alignment model.
  private final GradientOptimizer optimizer;
  
  private final boolean convex;
  
  public CfgAlignmentEmOracle(ParametricCfgAlignmentModel pam, SufficientStatistics smoothing,
      GradientOptimizer optimizer, boolean convex) {
    this.pam = Preconditions.checkNotNull(pam);
    this.smoothing = smoothing;
    this.optimizer = optimizer;
    
    this.convex = convex;
    
    Preconditions.checkArgument(pam.isLoglinear() || smoothing != null, 
        "Must provide smoothing if model does not use loglinear factors");
    
    Preconditions.checkArgument(!pam.isLoglinear() || optimizer != null,
        "Must provide a gradient optimizer if the alignment model uses loglinear factors.");
  }

  @Override
  public CfgAlignmentModel instantiateModel(SufficientStatistics parameters) {
    return pam.getModelFromParameters(parameters);
  }
  
  @Override
  public CfgExpectation getInitialExpectationAccumulator() {
    TableFactorBuilder rootBuilder = new TableFactorBuilder(
        pam.getRootFactor().getVars(), DenseTensorBuilder.getFactory());
    TableFactorBuilder ruleBuilder = new TableFactorBuilder(
        pam.getRuleFactor().getVars(), DenseTensorBuilder.getFactory());
    TableFactorBuilder nonterminalBuilder = new TableFactorBuilder(
        pam.getNonterminalFactor().getVars(), SparseTensorBuilder.getFactory());
    TableFactorBuilder terminalBuilder = new TableFactorBuilder(
        pam.getTerminalFactor().getVars(), SparseTensorBuilder.getFactory());
    return new CfgExpectation(rootBuilder, ruleBuilder, nonterminalBuilder, terminalBuilder);
  }

  @Override
  public CfgExpectation computeExpectations(CfgAlignmentModel model,
      SufficientStatistics currentParameters, AlignmentExample example,
      CfgExpectation accumulator, LogFunction log) {
    if (convex) {
      log.startTimer("e_step/getCfg");
      CfgParser parser = model.getUniformCfgParser(example);
      log.stopTimer("e_step/getCfg");

      log.startTimer("e_step/marginals");
      Factor rootFactor = model.getRootFactor(example.getTree(), parser.getParentVariable());
      CfgParseChart chart = parser.parseMarginal(example.getWords(), rootFactor, true);
      log.stopTimer("e_step/marginals");

      log.startTimer("e_step/compute_expectations");

      Factor terminalTreeExpectations = chart.getTerminalRuleExpectations();
      TableFactorBuilder builder = new TableFactorBuilder(terminalTreeExpectations.getVars(),
          DenseTensorBuilder.getFactory());
      model.populateTerminalDistribution(example.getWords(),
          parser.getParentVariable().getDiscreteVariables().get(0).getValues(),
          model.getTerminalFactor(), builder);
      DiscreteFactor terminalExpectations = terminalTreeExpectations.product(builder.build()).coerceToDiscrete();
      
      VariableNumMap varsExceptTerminal = terminalExpectations.getVars().removeAll(parser.getTerminalVariable());
      DiscreteFactor partitionFunction = terminalExpectations.marginalize(varsExceptTerminal);
      terminalExpectations = terminalExpectations.product(partitionFunction.inverse());

      pam.incrementExpectations(accumulator, chart.getMarginalEntriesRoot().coerceToDiscrete(),
          chart.getBinaryRuleExpectations().coerceToDiscrete(),
          terminalExpectations.coerceToDiscrete(), 1.0, chart.getPartitionFunction());

      log.stopTimer("e_step/compute_expectations");
      return accumulator;
    } else {
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
  }

  @Override
  public SufficientStatistics maximizeParameters(CfgExpectation expectations,
      SufficientStatistics currentParameters, LogFunction log) {
    if (pam.isLoglinear()) {
      List<SufficientStatistics> paramList = currentParameters.coerceToList().getStatistics();
      
      DiscreteFactor rootTarget = expectations.getRootBuilder().build();
      ParametricFactor rootFamily = pam.getRootFactor();
      SufficientStatistics rootParameters = trainFamily(rootFamily, rootTarget,
          VariableNumMap.EMPTY, paramList.get(0));
      
      DiscreteFactor ruleTarget = expectations.getRuleBuilder().build();
      ParametricFactor ruleFamily = pam.getRuleFactor();
      SufficientStatistics ruleParameters = trainFamily(ruleFamily, ruleTarget, pam.getNonterminalVar(),
          paramList.get(1));

      DiscreteFactor nonterminalTarget = expectations.getNonterminalBuilder().build();
      ParametricFactor nonterminalFamily = pam.getNonterminalFactor();
      SufficientStatistics nonterminalParameters = trainFamily(nonterminalFamily, nonterminalTarget,
          pam.getNonterminalVar().union(pam.getRuleVar()), paramList.get(2));

      DiscreteFactor terminalTarget = expectations.getTerminalBuilder().build();
      ParametricFactor terminalFamily = pam.getTerminalFactor();
      SufficientStatistics terminalParameters = trainFamily(terminalFamily, terminalTarget,
          pam.getNonterminalVar().union(pam.getRuleVar()), paramList.get(3));

      return new ListSufficientStatistics(Arrays.asList("root", "rules", "nonterminals", "terminals"),
          Arrays.asList(rootParameters, ruleParameters, nonterminalParameters, terminalParameters));
    } else {
      SufficientStatistics aggregate = pam.getNewSufficientStatistics();
      aggregate.increment(smoothing, 1.0);
      pam.incrementSufficientStatistics(expectations, aggregate, currentParameters, 1.0);
      return aggregate;
    }
  }
  
  private SufficientStatistics trainFamily(ParametricFactor family, Factor target,
      VariableNumMap conditionalVars, SufficientStatistics currentParameters) {
    SufficientStatistics optimizedParameters = null;
    try {
      FactorLoglikelihoodOracle oracle = new FactorLoglikelihoodOracle(family, target, conditionalVars);
      optimizedParameters = optimizer.train(oracle, currentParameters.duplicate(), Arrays.asList((Void) null));
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
