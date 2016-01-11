package com.jayantkrish.jklol.ccg;

import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.chart.ChartCost;
import com.jayantkrish.jklol.ccg.chart.SumChartCost;
import com.jayantkrish.jklol.ccg.chart.SyntacticChartCost;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionComparator;
import com.jayantkrish.jklol.inference.MarginalCalculator.ZeroProbabilityError;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.GradientOracle;
import com.jayantkrish.jklol.training.LogFunction;

public class CcgPerceptronOracle implements GradientOracle<CcgParser, CcgExample> {

  private final ParametricCcgParser family;
  
  // Function for comparing the equality of logical forms.
  private final ExpressionComparator comparator;

  private final CcgInference inferenceAlgorithm;

  private final double marginCost;

  /**
   * Create a gradient oracle for training a CCG with either 
   * a max-margin or perceptron objective.
   * 
   * @param family the family of CCG parsers 
   * @param comparator function for comparing equality of logical
   * forms. May be {@code null} if logical forms will not be used
   * for training.
   * @param inferenceAlgorithm
   * @param marginCost
   */
  public CcgPerceptronOracle(ParametricCcgParser family, ExpressionComparator comparator,
      CcgInference inferenceAlgorithm, double marginCost) {
    this.family = Preconditions.checkNotNull(family);
    this.comparator = comparator;
    this.inferenceAlgorithm = Preconditions.checkNotNull(inferenceAlgorithm);

    this.marginCost = marginCost;
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
  public double accumulateGradient(SufficientStatistics gradient,
      SufficientStatistics currentParameters, CcgParser instantiatedParser,
      CcgExample example, LogFunction log) {
    // Gradient is the features of the correct CCG parse minus the
    // features of the best predicted parse.

    // Calculate the best predicted parse, i.e., the highest weight parse
    // without conditioning on the true parse.
    log.startTimer("update_gradient/unconditional_max_marginal");
    ChartCost maxMarginCost = null;
    if (marginCost > 0.0 && example.getSyntacticParse() != null) {
      maxMarginCost = new SyntacticChartCost(example.getSyntacticParse(), 0.0, marginCost);
    }
    CcgParse bestPredictedParse = inferenceAlgorithm.getBestParse(instantiatedParser, example.getSentence(),
        maxMarginCost, log);
    if (bestPredictedParse == null) {
      // System.out.println("Search error (Predicted): " + example.getSentence());
      log.stopTimer("update_gradient/unconditional_max_marginal");
      throw new ZeroProbabilityError();
    }
    log.stopTimer("update_gradient/unconditional_max_marginal");

    // Calculate the best conditional parse, i.e., the highest weight parse
    // with the correct syntactic tree and set of semantic dependencies.
    log.startTimer("update_gradient/conditional_max_marginal");
    CcgParse bestCorrectParse = getBestConditionalParse(inferenceAlgorithm, instantiatedParser, comparator,
        example, log);
    if (bestCorrectParse == null) {
      // Search error: couldn't find any correct parses.
      System.out.println("Search error (Correct): " + example.getSentence() + " " + example.getLogicalForm());
      System.out.println("predicted: " + bestPredictedParse.getLogicalForm());
      // System.out.println("Expected tree: " + example.getSyntacticParse());
      // System.out.println("Search error cause: " + conditionalChartFilter.analyzeParseFailure());
      log.stopTimer("update_gradient/conditional_max_marginal");
      throw new ZeroProbabilityError();
    }
    log.stopTimer("update_gradient/conditional_max_marginal");

    // System.out.println("best predicted: " + bestPredictedParse + " " + bestPredictedParse.getSubtreeProbability());
    // System.out.println("best correct:   " + bestCorrectParse + " " + bestCorrectParse.getSubtreeProbability());

    log.startTimer("update_gradient/increment_gradient");
    // Subtract the predicted feature counts.
    family.incrementSufficientStatistics(gradient, currentParameters,
        example.getSentence(), bestPredictedParse, -1.0);
    // Add the feature counts of best correct parse.
    family.incrementSufficientStatistics(gradient, currentParameters,
        example.getSentence(), bestCorrectParse, 1.0);
    log.stopTimer("update_gradient/increment_gradient");

    // Return the amount by which the predicted parse's score exceeds the
    // true parse. (Negate this value, because this is a maximization problem)
    return Math.min(0.0, Math.log(bestCorrectParse.getSubtreeProbability())
        - Math.log(bestPredictedParse.getSubtreeProbability()));
  }
  
  public static CcgParse getBestConditionalParse(CcgInference inference, CcgParser parser,
      ExpressionComparator comparator, CcgExample example, LogFunction log) {
    
    ChartCost syntacticCost = null;
    if (example.hasSyntacticParse()) {
      syntacticCost = SyntacticChartCost.createAgreementCost(example.getSyntacticParse());
    }
    ChartCost cost = SumChartCost.create(syntacticCost);
    
    if (example.hasDependencies() || example.hasLogicalForm()) {
      // Have to use approximate inference. 
      List<CcgParse> possibleParses = inference.beamSearch(parser, example.getSentence(), cost, log);
      if (example.hasDependencies()) {
        possibleParses = CcgLoglikelihoodOracle.filterParsesByDependencies(example.getDependencies(),
            possibleParses);
      }

      if (example.hasLogicalForm()) {
        possibleParses = CcgLoglikelihoodOracle.filterParsesByLogicalForm(example.getLogicalForm(),
            comparator, possibleParses);
      }

      if (possibleParses.size() > 0) {
        return possibleParses.get(0);
      } else {
        return null;
      }
    } else {
      return inference.getBestParse(parser, example.getSentence(), cost, log);
    }
  }
}
