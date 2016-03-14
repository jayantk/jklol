package com.jayantkrish.jklol.ccg;

import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.chart.ChartCost;
import com.jayantkrish.jklol.ccg.chart.SumChartCost;
import com.jayantkrish.jklol.ccg.chart.SyntacticChartCost;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionComparator;
import com.jayantkrish.jklol.inference.MarginalCalculator.ZeroProbabilityError;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.training.GradientOracle;
import com.jayantkrish.jklol.training.LogFunction;

/**
 * Trains a CCG from observed CCG dependency structures 
 * or logical forms using a loglikelihood objective function
 * with approximate inference (beam search). 
 * 
 * @author jayant
 */
public class CcgLoglikelihoodOracle implements GradientOracle<CcgParser, CcgExample> {

  private final ParametricCcgParser family;
  
  // Function for comparing the equality of logical forms.
  private final ExpressionComparator comparator;
  
  private final CcgInference inference;

  /**
   * 
   * @param family
   * @param comparator may be {@code null} if logical forms are not
   * to be used for training.
   * @param inference
   */
  public CcgLoglikelihoodOracle(ParametricCcgParser family, ExpressionComparator comparator, 
      CcgInference inference) {
    this.family = Preconditions.checkNotNull(family);
    this.comparator = comparator;
    this.inference = Preconditions.checkNotNull(inference);
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
    AnnotatedSentence sentence = example.getSentence();

    // Gradient is the feature expectations of all correct CCG parses, minus all
    // CCG parses.
    log.startTimer("update_gradient/input_marginal");
    // Calculate the unconditional distribution over CCG parses.
    List<CcgParse> parses = inference.beamSearch(instantiatedParser, sentence, null, log);
        
    if (parses.size() == 0) {
      // Search error: couldn't find any parses.
      System.out.println("Search error (Predicted): " + sentence + " " + example.getLogicalForm());
      throw new ZeroProbabilityError();      
    }
    log.stopTimer("update_gradient/input_marginal");

    // Find parses with the correct syntactic structure and dependencies.
    log.startTimer("update_gradient/condition_parses");
    // Condition parses on provided syntactic / lexicon information,
    // if any is provided.
    ChartCost syntacticCost = null;
    if (example.hasSyntacticParse()) {
      syntacticCost = SyntacticChartCost.createAgreementCost(example.getSyntacticParse());
    }
    ChartCost cost = SumChartCost.create(syntacticCost);

    List<CcgParse> possibleParses = null;
    if (cost != null) {
      possibleParses = inference.beamSearch(instantiatedParser, sentence, cost, log);
    } else {
      possibleParses = Lists.newArrayList(parses);
    }

    List<CcgParse> correctParses = possibleParses;
    // Condition on correct dependency structures, if provided.
    if (example.getDependencies() != null) {
      correctParses = filterParsesByDependencies(example.getDependencies(),
          correctParses);
    }
    // Condition on correct logical form, if provided.
    if (example.hasLogicalForm()) {
      correctParses = filterParsesByLogicalForm(example.getLogicalForm(), comparator, correctParses);
    }
    log.stopTimer("update_gradient/condition_parses");

    if (correctParses.size() == 0) {
      // Search error: couldn't find any correct parses.
      System.out.println("Search error (Correct): " + sentence + " " + example.getLogicalForm());
      throw new ZeroProbabilityError();
    }

    log.startTimer("update_gradient/increment_gradient");
    // Subtract the unconditional expected feature counts.
    double unconditionalPartitionFunction = getPartitionFunction(parses);
    for (CcgParse parse : parses) {
      family.incrementSufficientStatistics(gradient, currentParameters, sentence, parse, -1.0 * 
          parse.getSubtreeProbability() / unconditionalPartitionFunction);
    }

    // Add conditional expected feature counts.
    double conditionalPartitionFunction = getPartitionFunction(correctParses);
    for (CcgParse parse : correctParses) {
      family.incrementSufficientStatistics(gradient, currentParameters, sentence, parse,
          parse.getSubtreeProbability() / conditionalPartitionFunction);
    }
    log.stopTimer("update_gradient/increment_gradient");
    
    // The difference in log partition functions is equivalent to the loglikelihood
    // assigned to all correct parses.
    return Math.log(conditionalPartitionFunction) - Math.log(unconditionalPartitionFunction);
  }

  public static List<CcgParse> filterParsesByDependencies(Set<DependencyStructure> observedDependencies,
      Iterable<CcgParse> parses) {
    Preconditions.checkNotNull(observedDependencies);
    List<CcgParse> correctParses = Lists.newArrayList();
    
    for (CcgParse parse : parses) {
      if (Sets.newHashSet(parse.getAllDependencies()).equals(observedDependencies)) {
        correctParses.add(parse);
      }
    }
    return correctParses;
  }
  
  public static List<CcgParse> filterParsesByLogicalForm(Expression2 observedLogicalForm,
    ExpressionComparator comparator, Iterable<CcgParse> parses) {
    Preconditions.checkNotNull(observedLogicalForm);

    List<CcgParse> correctParses = Lists.newArrayList();
    for (CcgParse parse : parses) {
      Expression2 predictedLogicalForm = parse.getLogicalForm();

      if (predictedLogicalForm != null && comparator.equals(predictedLogicalForm, observedLogicalForm)){
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
