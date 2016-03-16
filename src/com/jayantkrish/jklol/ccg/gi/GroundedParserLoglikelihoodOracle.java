package com.jayantkrish.jklol.ccg.gi;

import java.util.Collection;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.chart.ChartCost;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.inference.MarginalCalculator.ZeroProbabilityError;
import com.jayantkrish.jklol.lisp.inc.IncEvalCost;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.training.GradientOracle;
import com.jayantkrish.jklol.training.LogFunction;

public class GroundedParserLoglikelihoodOracle implements 
  GradientOracle<GroundedParser, GroundedParseExample> {
  
  private final ParametricGroundedParser family;
  private final GroundedParserInference inference;
  private final ExpressionSimplifier simplifier;

  public GroundedParserLoglikelihoodOracle(ParametricGroundedParser family,
      GroundedParserInference inference) {
    this.family = Preconditions.checkNotNull(family);
    this.inference = inference;
    this.simplifier = ExpressionSimplifier.lambdaCalculus();
  }
  
  @Override
  public SufficientStatistics initializeGradient() {
    return family.getNewSufficientStatistics();
  }

  @Override
  public GroundedParser instantiateModel(SufficientStatistics parameters) {
    return family.getModelFromParameters(parameters);
  }

  @Override
  public double accumulateGradient(SufficientStatistics gradient,
      SufficientStatistics currentParameters, GroundedParser model,
      GroundedParseExample example, LogFunction log) {
    AnnotatedSentence sentence = example.getSentence();
    Object diagram = example.getDiagram();
    
    System.out.println(sentence);
    System.out.println(diagram);

    // Get a distribution on executions conditioned on the label of the example.
    // Do this first because it's faster, so search errors take less time to process.
    log.startTimer("update_gradient/output_marginal");
    IncEvalCost labelCost = example.getLabelCost();
    ChartCost chartFilter = example.getChartFilter();
    System.out.println("conditional evaluations:");
    List<GroundedCcgParse> conditionalParsesInit = inference.beamSearch(
        model, sentence, diagram, chartFilter, labelCost, log);
    
    List<GroundedCcgParse> conditionalParses = Lists.newArrayList();
    for (GroundedCcgParse parse : conditionalParsesInit) {
      Expression2 lf = parse.getLogicalForm();
      if (lf != null) {
        lf = simplifier.apply(lf);
      }

      if (example.isCorrectDenotation(parse.getDenotation(), parse.getDiagram())) {
        conditionalParses.add(parse);
        // System.out.println(parse.getSubtreeProbability() + " " + lf + " " + parse.getSyntacticParse());
      }
    }
    
    if (conditionalParses.size() == 0) {
      System.out.println("Search error (Correct): " + sentence);
      throw new ZeroProbabilityError();
    }
    log.stopTimer("update_gradient/output_marginal");
    
    // Get a distribution over unconditional executions.
    log.startTimer("update_gradient/input_marginal");
    IncEvalCost marginCost = example.getMarginCost();
    System.out.println("unconditional evaluations:");
    List<GroundedCcgParse> unconditionalParses = inference.beamSearch(
        model, sentence, diagram, null, marginCost, log);
    
    if (unconditionalParses.size() == 0) {
      System.out.println("Search error (Predicted): " + sentence);
      throw new ZeroProbabilityError();      
    }
    log.stopTimer("update_gradient/input_marginal");

    log.startTimer("update_gradient/increment_gradient");
    double unconditionalPartitionFunction = getPartitionFunction(unconditionalParses);
    for (GroundedCcgParse parse : unconditionalParses) {
      family.incrementSufficientStatistics(gradient, currentParameters, sentence, diagram, parse,
          -1.0 * parse.getSubtreeProbability() / unconditionalPartitionFunction);
    }

    double conditionalPartitionFunction = getPartitionFunction(conditionalParses);
    for (GroundedCcgParse parse : conditionalParses) {
      family.incrementSufficientStatistics(gradient, currentParameters, sentence, diagram, parse,
          parse.getSubtreeProbability() / conditionalPartitionFunction);
    }
    log.stopTimer("update_gradient/increment_gradient");

    // System.out.println(family.getParameterDescription(gradient));
    
    // Note that the returned loglikelihood is an approximation because
    // inference is approximate.
    return Math.log(conditionalPartitionFunction) - Math.log(unconditionalPartitionFunction);
  }
  
  public double getPartitionFunction(Collection<GroundedCcgParse> parses) {
    double partitionFunction = 0.0;
    for (GroundedCcgParse parse : parses) {
      partitionFunction += parse.getSubtreeProbability();
    }
    return partitionFunction;
  }
}
