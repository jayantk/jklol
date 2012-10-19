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
    return family.getParserFromParameters(parameters);
  }

  @Override
  public double accumulateGradient(SufficientStatistics gradient, CcgParser instantiatedParser, 
      CcgExample example, LogFunction log) {
    
    // Gradient is the feature expectations of all correct CCG parses, minus all CCG parses.   
    log.startTimer("update_gradient/input_marginal");
    // Calculate the unconditional distribution over CCG parses.
    List<CcgParse> parses = instantiatedParser.beamSearch(example.getWords(), beamSize);
    log.stopTimer("update_gradient/input_marginal");
    
    log.startTimer("update_gradient/identify_correct_parses");
    // Calculate the distribution over correct CCG parses.
    List<CcgParse> correctParses = Lists.newArrayList();
    Set<DependencyStructure> observedDependencies = example.getDependencies();
    for (CcgParse parse : parses) {
      if (Sets.newHashSet(parse.getAllDependencies()).equals(observedDependencies)) {
        correctParses.add(parse);
      }
    }
    log.stopTimer("update_gradient/identify_correct_parses");

    if (correctParses.size() == 0) {
      // Search error: couldn't find any correct parses.
      throw new ZeroProbabilityError();
    }

    // Subtract the unconditional expected feature counts
    log.startTimer("update_gradient/increment_gradient");
    double unconditionalPartitionFunction = getPartitionFunction(parses);
    for (CcgParse parse : parses) {
      family.incrementSufficientStatistics(gradient, parse, -1.0 / unconditionalPartitionFunction);
    }
    
    double conditionalPartitionFunction = getPartitionFunction(correctParses);
    for (CcgParse parse : correctParses) {
      family.incrementSufficientStatistics(gradient, parse, 1.0 / conditionalPartitionFunction);
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
