package com.jayantkrish.jklol.ccg;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.inference.MarginalCalculator.ZeroProbabilityError;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.GradientOracle;
import com.jayantkrish.jklol.training.LogFunction;

public class CcgPerceptronOracle implements GradientOracle<CcgParser, CcgExample> {

  private final ParametricCcgParser family;
  private final CcgInference inferenceAlgorithm;

  public CcgPerceptronOracle(ParametricCcgParser family, CcgInference inferenceAlgorithm) {
    this.family = Preconditions.checkNotNull(family);
    this.inferenceAlgorithm = Preconditions.checkNotNull(inferenceAlgorithm);
  }

  @Override
  public SufficientStatistics initializeGradient() {
    return family.getNewSufficientStatistics();
  }

  @Override
  public CcgParser instantiateModel(SufficientStatistics parameters) {
    return family.getModelFromParameters(parameters);
  }

  @Override
  public double accumulateGradient(SufficientStatistics gradient, CcgParser instantiatedParser,
      CcgExample example, LogFunction log) {
    // Gradient is the features of the correct CCG parse minus the
    // features of the best predicted parse.

    // Calculate the best predicted parse, i.e., the highest weight parse
    // without conditioning on the true parse.
    log.startTimer("update_gradient/unconditional_max_marginal");
    CcgParse bestPredictedParse = inferenceAlgorithm.getBestParse(instantiatedParser, example.getSentence(),
        null, log);
    if (bestPredictedParse == null) {
      System.out.println("Search error (Predicted): " + example.getSentence());
      throw new ZeroProbabilityError();
    }
    log.stopTimer("update_gradient/unconditional_max_marginal");

    // Calculate the best conditional parse, i.e., the highest weight parse
    // with the correct syntactic tree and set of semantic dependencies.
    log.startTimer("update_gradient/conditional_max_marginal");
    CcgParse bestCorrectParse = inferenceAlgorithm.getBestConditionalParse(instantiatedParser,
        example.getSentence(), null, log, example.getSyntacticParse(),
        example.getDependencies(), example.getLogicalForm());
    if (bestCorrectParse == null) {
      // Search error: couldn't find any correct parses.
      System.out.println("Search error (Correct): " + example.getSentence());
      System.out.println("Expected tree: " + example.getSyntacticParse());
      // System.out.println("Search error cause: " + conditionalChartFilter.analyzeParseFailure());
      throw new ZeroProbabilityError();
    }
    log.stopTimer("update_gradient/conditional_max_marginal");
    System.out.println("best predicted: " + bestPredictedParse + " " + bestPredictedParse.getSubtreeProbability());
    System.out.println("best correct:   " + bestCorrectParse + " " + bestCorrectParse.getSubtreeProbability());

    log.startTimer("update_gradient/increment_gradient");
    // Subtract the predicted feature counts.
    family.incrementSufficientStatistics(gradient, bestPredictedParse, -1.0);
    // Add the feature counts of best correct parse.
    family.incrementSufficientStatistics(gradient, bestCorrectParse, 1.0);
    log.stopTimer("update_gradient/increment_gradient");

    // It's not clear what the correct objective value should be.
    return 0;
  }
}
