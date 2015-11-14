package com.jayantkrish.jklol.ccg.lexinduct;

import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.lexinduct.LagrangianAlignmentDecoder.LagrangianDecodingResult;
import com.jayantkrish.jklol.cfg.CfgExpectation;
import com.jayantkrish.jklol.cfg.CfgParseTree;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;

public class LagrangianAlignmentTrainer {
  
  private final int numIterations;
  private final LagrangianAlignmentDecoder decoder;

  public LagrangianAlignmentTrainer(int numIterations, LagrangianAlignmentDecoder decoder) {
    this.numIterations = numIterations;
    this.decoder = decoder;
  }

  public ParametersAndLagrangeMultipliers train(ParametricCfgAlignmentModel pam,
      SufficientStatistics initialParameters, SufficientStatistics smoothing, List<AlignmentExample> trainingData,
      DiscreteFactor lexiconFactor) {

    SufficientStatistics parameters = initialParameters;
    SufficientStatistics nextParameters = pam.getNewSufficientStatistics();

    TableFactorBuilder ruleBuilder = new TableFactorBuilder(
        pam.getRuleFactor().getVars(), DenseTensorBuilder.getFactory());
    TableFactorBuilder nonterminalBuilder = new TableFactorBuilder(
        pam.getNonterminalFactor().getVars(), DenseTensorBuilder.getFactory());
    TableFactorBuilder terminalBuilder = new TableFactorBuilder(
        pam.getTerminalFactor().getVars(), DenseTensorBuilder.getFactory());
    CfgExpectation accumulator = new CfgExpectation(ruleBuilder, nonterminalBuilder, terminalBuilder);

    LagrangianDecodingResult result = null;
    for (int i = 0; i < numIterations; i++) {
      CfgAlignmentModel model = pam.getModelFromParameters(parameters);

      // E-step
      result = decoder.decode(model, trainingData, lexiconFactor);
      
      // M-step
      accumulator.zeroOut();
      for (CfgParseTree parse : result.getParseTrees()) {
        pam.incrementExpectations(accumulator, parse, 1.0);
      }

      nextParameters.zeroOut();
      nextParameters.increment(smoothing, 1.0);
      pam.incrementSufficientStatistics(accumulator, nextParameters, parameters, 1.0);

      parameters.zeroOut();
      parameters.increment(nextParameters, 1.0);
    }
    return new ParametersAndLagrangeMultipliers(parameters, result);
  }
  
  public static class ParametersAndLagrangeMultipliers {
    private final SufficientStatistics parameters;
    private final LagrangianDecodingResult result;
    
    public ParametersAndLagrangeMultipliers(SufficientStatistics parameters,
        LagrangianDecodingResult result) {
      this.parameters = Preconditions.checkNotNull(parameters);
      this.result = Preconditions.checkNotNull(result);
    }

    public SufficientStatistics getParameters() {
      return parameters;
    }

    public LagrangianDecodingResult getLagrangeMultipliers() {
      return result;
    }
  }
}
