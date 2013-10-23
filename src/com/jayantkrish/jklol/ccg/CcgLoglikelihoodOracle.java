package com.jayantkrish.jklol.ccg;

import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.chart.ChartFilter;
import com.jayantkrish.jklol.ccg.chart.SyntacticChartFilter;
import com.jayantkrish.jklol.ccg.lambda.Expression;
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
    List<CcgParse> parses = instantiatedParser.beamSearch(example.getWords(), example.getPosTags(), 
        beamSize, log);
    if (parses.size() == 0) {
      // Search error: couldn't find any parses.
      throw new ZeroProbabilityError();      
    }
    log.stopTimer("update_gradient/input_marginal");

    // Find parses with the correct syntactic structure and dependencies.
    log.startTimer("update_gradient/condition_parses_on_dependencies");
    // Condition parses on provided syntactic information, if any is provided.
    List<CcgParse> possibleParses = null;
    if (example.hasSyntacticParse()) {
      ChartFilter conditionalChartFilter = new SyntacticChartFilter(example.getSyntacticParse());
      possibleParses = instantiatedParser.beamSearch(example.getWords(), example.getPosTags(), beamSize,
        conditionalChartFilter, log, -1);
    } else {
      possibleParses = Lists.newArrayList(parses);
    }

    // Condition on true dependency structures, if provided.
    List<CcgParse> correctParses = filterSemanticallyCompatibleParses(example.getDependencies(),
        example.getLogicalForm(), possibleParses);

    if (correctParses.size() == 0) {
      // Search error: couldn't find any correct parses.
      throw new ZeroProbabilityError();
    }
    log.stopTimer("update_gradient/condition_parses_on_dependencies");

    log.startTimer("update_gradient/increment_gradient");
    // Subtract the unconditional expected feature counts.
    double unconditionalPartitionFunction = getPartitionFunction(parses);
    for (CcgParse parse : parses) {
      family.incrementSufficientStatistics(gradient, parse, -1.0 * 
          parse.getSubtreeProbability() / unconditionalPartitionFunction);
    }

    // Add conditional expected feature counts.
    double conditionalPartitionFunction = getPartitionFunction(correctParses);
    for (CcgParse parse : correctParses) {
      family.incrementSufficientStatistics(gradient, parse, parse.getSubtreeProbability() 
          / conditionalPartitionFunction);
    }
    log.stopTimer("update_gradient/increment_gradient");

    // The difference in log partition functions is equivalent to the loglikelihood
    // assigned to all correct parses.
    return Math.log(conditionalPartitionFunction) - Math.log(unconditionalPartitionFunction);
  }
  
  public static List<CcgParse> filterSemanticallyCompatibleParses(Set<DependencyStructure> observedDependencies,
      Expression observedLogicalForm, Iterable<CcgParse> parses) {
    List<CcgParse> correctParses = Lists.newArrayList();
    
    for (CcgParse parse : parses) {
      boolean compatible = true;
      if (observedDependencies != null && !Sets.newHashSet(parse.getAllDependencies()).equals(observedDependencies)) {
        compatible = false;
      }
      if (observedLogicalForm != null) {
        Expression predictedLogicalForm = parse.getLogicalForm();
        if (predictedLogicalForm == null || !predictedLogicalForm.simplify().functionallyEquals(observedLogicalForm.simplify())) {
          compatible = false;
        }
      }

      if (compatible) {
        correctParses.add(parse);
      }
    }
    return correctParses;
  }

  private double getPartitionFunction(List<CcgParse> parses) {
    double partitionFunction = 0.0;
    for (CcgParse parse : parses) {
      partitionFunction += parse.getSubtreeProbability();
    }
    return partitionFunction;
  }
}
