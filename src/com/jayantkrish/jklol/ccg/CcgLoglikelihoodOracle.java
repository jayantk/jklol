package com.jayantkrish.jklol.ccg;

import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.inference.MarginalCalculator.ZeroProbabilityError;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.GradientOracle;
import com.jayantkrish.jklol.training.LogFunction;

/**
 * Trains a CCG from observed CCG dependency structures.
 * 
 * @author jayant
 */
public class CcgLoglikelihoodOracle implements GradientOracle<CcgParser, CcgExample> {

  private final ParametricCcgParser family;
  // Size of the beam used during inference (which uses beam search).
  private final int beamSize;

  public CcgLoglikelihoodOracle(ParametricCcgParser family, int beamSize) {
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

    // Gradient is the feature expectations of all correct CCG parses, minus all
    // CCG parses.
    log.startTimer("update_gradient/input_marginal");
    // Calculate the unconditional distribution over CCG parses.
    List<CcgParse> parses = instantiatedParser.beamSearch(example.getWords(), beamSize, log);
    if (parses.size() == 0) {
      // Search error: couldn't find any parses.
      throw new ZeroProbabilityError();      
    }
    log.stopTimer("update_gradient/input_marginal");

    List<CcgParse> correctParses = null;
    if (!example.hasLexiconEntries()) {
      log.startTimer("update_gradient/condition_parses_on_dependencies");
      // If both the correct dependencies and lexicon entries are observed,
      // we can compute the gradient without conditioning the predicted parses
      // on the observed dependencies. If the lexicon entries are not observed,
      // then we must condition.
      correctParses = Lists.newArrayList();
      Set<DependencyStructure> observedDependencies = example.getDependencies();
      for (CcgParse parse : parses) {
        if (Sets.newHashSet(parse.getAllDependencies()).equals(observedDependencies)) {
          correctParses.add(parse);
        }
      }

      if (correctParses.size() == 0) {
        // Search error: couldn't find any correct parses.
        throw new ZeroProbabilityError();
      }
      log.stopTimer("update_gradient/condition_parses_on_dependencies");
    }

    log.startTimer("update_gradient/increment_gradient");
    // Subtract the unconditional expected feature counts.
    double unconditionalPartitionFunction = getPartitionFunction(parses);
    for (CcgParse parse : parses) {
      family.incrementSufficientStatistics(gradient, parse, -1.0 * 
          parse.getSubtreeProbability() / unconditionalPartitionFunction);
    }

    // Add the truth-conditional expected feature counts.
    double conditionalPartitionFunction = -1.0;
    if (example.hasLexiconEntries()) {
      // If both the correct dependencies and lexicon entries are observed, no
      // inference is necessary. Simply increment the gradient using the correct
      // answers.
      // TODO: this partition function is wrong... Should be the probability
      // of observing the given dependencies and lexicon entries.
      conditionalPartitionFunction = 1.0;
      family.incrementDependencySufficientStatistics(gradient, example.getDependencies(), 1.0);
      family.incrementLexiconSufficientStatistics(gradient, example.getLexiconEntries(), 1.0);
    } else {
      // The correct lexicon entries were unobserved. In this case, we condition
      // the parses produced by our beam search on the true dependencies (above) to
      // compute (approximate) expected feature counts for the lexicon entries.
      conditionalPartitionFunction = getPartitionFunction(correctParses);
      for (CcgParse parse : correctParses) {
        family.incrementSufficientStatistics(gradient, parse, parse.getSubtreeProbability() 
            / conditionalPartitionFunction);
      }
    }
    log.stopTimer("update_gradient/increment_gradient");

    return Math.log(conditionalPartitionFunction) - Math.log(unconditionalPartitionFunction);
  }

  private double getPartitionFunction(List<CcgParse> parses) {
    double partitionFunction = 0.0;
    for (CcgParse parse : parses) {
      partitionFunction += parse.getSubtreeProbability();
    }
    return partitionFunction;
  }
}
