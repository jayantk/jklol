package com.jayantkrish.jklol.p3;

import java.util.Collection;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.chart.ChartCost;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.inference.MarginalCalculator.ZeroProbabilityError;
import com.jayantkrish.jklol.lisp.inc.IncEvalCost;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.training.GradientOracle;
import com.jayantkrish.jklol.training.LogFunction;

public class P3LoglikelihoodOracle implements 
  GradientOracle<P3Model, P3Example> {
  
  private final ParametricP3Model family;
  private final P3Inference inference;
  private final ExpressionSimplifier simplifier;

  public P3LoglikelihoodOracle(ParametricP3Model family,
      P3Inference inference) {
    this.family = Preconditions.checkNotNull(family);
    this.inference = inference;
    this.simplifier = ExpressionSimplifier.lambdaCalculus();
  }
  
  @Override
  public SufficientStatistics initializeGradient() {
    return family.getNewSufficientStatistics();
  }

  @Override
  public P3Model instantiateModel(SufficientStatistics parameters) {
    return family.getModelFromParameters(parameters);
  }

  @Override
  public double accumulateGradient(SufficientStatistics gradient,
      SufficientStatistics currentParameters, P3Model model,
      P3Example example, LogFunction log) {
    AnnotatedSentence sentence = example.getSentence();
    Object diagram = example.getDiagram();
    
    System.out.println(sentence);
    System.out.println(diagram);

    // Get a distribution on executions conditioned on the label of the example.
    // Do this first because it's faster, so search errors take less time to process.
    log.startTimer("update_gradient/output_marginal");
    IncEvalCost labelCost = example.getLabelCost();
    ChartCost chartFilter = example.getChartFilter();
    List<P3Parse> conditionalParsesInit = inference.beamSearch(
        model, sentence, diagram, chartFilter, labelCost, log);
    
    List<P3Parse> conditionalParses = Lists.newArrayList();
    log.startTimer("update_gradient/output_marginal/filter");
    // System.out.println("conditional evaluations:");
    for (P3Parse parse : conditionalParsesInit) {
      if (example.isCorrect(parse.getLogicalForm(), parse.getDenotation(), parse.getDiagram())) {
        conditionalParses.add(parse);
        
        /*
        System.out.println(parse.getSubtreeProbability() + " " + parse.getDenotation() + " "
            + parse.getLogicalForm() + " " + parse.getSyntacticParse());
        KbState state = ((KbState) parse.getDiagram());
        for (int i : state.getUpdatedFunctionIndexes()) {
          System.out.println(state.getFunctions().get(i) + " " + state.getAssignments().get(i).getFeatureVector().toString());
          System.out.println("   " + state.getAssignments().get(i).getPredicateFeatureVector().toString());
        }
        */
      }
    }
    log.stopTimer("update_gradient/output_marginal/filter");
    
    if (conditionalParses.size() == 0) {
      System.out.println("Search error (Correct): " + sentence);
      throw new ZeroProbabilityError();
    }
    log.stopTimer("update_gradient/output_marginal");

    // Get a distribution over unconditional executions.
    log.startTimer("update_gradient/input_marginal");
    IncEvalCost marginCost = example.getMarginCost();
    // System.out.println("unconditional evaluations:");
    List<P3Parse> unconditionalParses = null;
    if (marginCost == null && chartFilter == null && labelCost == null) {
      unconditionalParses = conditionalParsesInit;
    } else {
      unconditionalParses = inference.beamSearch(
          model, sentence, diagram, null, marginCost, log);
    }
    
    if (unconditionalParses.size() == 0) {
      System.out.println("Search error (Predicted): " + sentence);
      throw new ZeroProbabilityError();      
    }
    log.stopTimer("update_gradient/input_marginal");

    log.startTimer("update_gradient/increment_gradient");
    double unconditionalPartitionFunction = getPartitionFunction(unconditionalParses);
    for (P3Parse parse : unconditionalParses) {
      family.incrementSufficientStatistics(gradient, currentParameters, sentence, diagram, parse,
          -1.0 * parse.getSubtreeProbability() / unconditionalPartitionFunction);
    }

    double conditionalPartitionFunction = getPartitionFunction(conditionalParses);
    for (P3Parse parse : conditionalParses) {
      family.incrementSufficientStatistics(gradient, currentParameters, sentence, diagram, parse,
          parse.getSubtreeProbability() / conditionalPartitionFunction);
    }
    log.stopTimer("update_gradient/increment_gradient");

    // System.out.println(family.getParameterDescription(gradient));
    
    // Note that the returned loglikelihood is an approximation because
    // inference is approximate.
    return Math.log(conditionalPartitionFunction) - Math.log(unconditionalPartitionFunction);
  }
  
  public double getPartitionFunction(Collection<P3Parse> parses) {
    double partitionFunction = 0.0;
    for (P3Parse parse : parses) {
      partitionFunction += parse.getSubtreeProbability();
    }
    return partitionFunction;
  }
}
