package com.jayantkrish.jklol.ccg;

import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.CcgChart.ChartFilter;
import com.jayantkrish.jklol.inference.MarginalCalculator.ZeroProbabilityError;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.GradientOracle;
import com.jayantkrish.jklol.training.LogFunction;

public class CcgPerceptronOracle implements GradientOracle<CcgParser, CcgExample> {

  private final ParametricCcgParser family;
  // Size of the beam used during inference (which uses beam search).
  private final int beamSize;

  public CcgPerceptronOracle(ParametricCcgParser family, int beamSize) {
    this.family = Preconditions.checkNotNull(family);
    this.beamSize = beamSize;
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
    log.startTimer("update_gradient/unconditional_max_marginal");
    // Calculate the best predicted parse, i.e., the highest weight parse
    // without conditioning on the true parse.
    List<CcgParse> parses = instantiatedParser.beamSearch(example.getWords(), example.getPosTags(),
        beamSize, null, log);
    CcgParse bestPredictedParse = parses.size() > 0 ? parses.get(0) : null;
    System.out.println("num predicted: " + parses.size());
    if (bestPredictedParse == null) {
      System.out.println("Search error (Predicted): " + example.getWords());
      throw new ZeroProbabilityError();
    }
    log.stopTimer("update_gradient/unconditional_max_marginal");

    log.startTimer("update_gradient/conditional_max_marginal");
    ChartFilter conditionalChartFilter = new SyntacticChartFilter(example.getSyntacticParse());
    List<CcgParse> possibleParses = instantiatedParser.beamSearch(example.getWords(),
        example.getPosTags(), beamSize, conditionalChartFilter, log);
    CcgParse bestCorrectParse = possibleParses.size() > 0 ? possibleParses.get(0) : null;
    if (example.hasDependencies()) {
      Set<DependencyStructure> observedDependencies = example.getDependencies();
      for (CcgParse parse : possibleParses) {
        if (Sets.newHashSet(parse.getAllDependencies()).equals(observedDependencies)) {
          bestCorrectParse = parse;
          break;
        }
      }
    }

    System.out.println("num correct: " + possibleParses.size());
    if (bestCorrectParse == null) {
      // Search error: couldn't find any correct parses.
      System.out.println("Search error (Correct): " + example.getWords());
      throw new ZeroProbabilityError();
    }        
    log.stopTimer("update_gradient/conditional_max_marginal");
    System.out.println("best predicted: " + bestPredictedParse + " " + bestPredictedParse.getSubtreeProbability());
    System.out.println("best correct: " + bestCorrectParse + " " + bestCorrectParse.getSubtreeProbability());

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
