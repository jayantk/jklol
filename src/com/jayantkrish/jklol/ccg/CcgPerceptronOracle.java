package com.jayantkrish.jklol.ccg;

import java.util.List;

import com.google.common.base.Preconditions;
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
    log.startTimer("update_gradient/input_parse");
    // Calculate the unconditional distribution over CCG parses.
    List<CcgParse> parses = instantiatedParser.beamSearch(example.getWords(), beamSize, log);
    CcgParse bestPredictedParse = null;
    for (CcgParse parse : parses) {
      if (parse.getSyntacticCategory().isAtomic()) {
        bestPredictedParse = parse;
        break;
      }
    }

    if (bestPredictedParse == null) {
      // Search error: couldn't find any parses with atomic category heads.
      throw new ZeroProbabilityError();
    }
    log.stopTimer("update_gradient/input_parse");

    log.startTimer("update_gradient/increment_gradient");
    // Subtract the predicted feature counts.
    family.incrementSufficientStatistics(gradient, bestPredictedParse, -1.0);

    // Add the feature counts of the true parse.
    Preconditions.checkState(example.hasLexiconEntries(), 
        "Training example doesn't have lexicon entries: %s", example); 
    family.incrementDependencySufficientStatistics(gradient, example.getDependencies(), 1.0);
    family.incrementLexiconSufficientStatistics(gradient, example.getLexiconEntries(), 1.0);
    log.stopTimer("update_gradient/increment_gradient");
    // It's not clear what the correct objective value should be.
    return 0;
  }
}
