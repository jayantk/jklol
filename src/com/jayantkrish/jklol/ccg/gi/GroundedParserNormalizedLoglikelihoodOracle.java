package com.jayantkrish.jklol.ccg.gi;

import java.util.List;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.inference.MarginalCalculator.ZeroProbabilityError;
import com.jayantkrish.jklol.lisp.inc.IncEval;
import com.jayantkrish.jklol.lisp.inc.IncEval.IncEvalState;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.training.GradientOracle;
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.training.NullLogFunction;

public class GroundedParserNormalizedLoglikelihoodOracle 
  implements GradientOracle<GroundedParser, GroundedParseExample> {

  private final ParametricGroundedParser family;
  private final ExpressionSimplifier simplifier;
  private final int parserBeamSize;
  private final int evalBeamSize;
  
  public GroundedParserNormalizedLoglikelihoodOracle(ParametricGroundedParser family, 
      ExpressionSimplifier simplifier, int parserBeamSize, int evalBeamSize) {
    this.family = Preconditions.checkNotNull(family);
    this.simplifier = Preconditions.checkNotNull(simplifier);
    this.parserBeamSize = parserBeamSize;
    this.evalBeamSize = evalBeamSize;
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
    
    System.out.println("sentence: " + sentence);
    System.out.println("diagram: " + diagram);
    
    CcgParser parser = model.getCcgParser();
    List<CcgParse> unconditionalParses = parser.beamSearch(sentence, parserBeamSize);
    List<CcgParse> conditionalParses = null;
    if (example.getChartFilter() != null) {
      conditionalParses = parser.beamSearch(sentence, parserBeamSize,
          example.getChartFilter(), new NullLogFunction(), -1, Integer.MAX_VALUE, 1);
    } else {
      conditionalParses = unconditionalParses;
    }
    double parserPartitionFunction = getParsePartitionFunction(unconditionalParses); 
    
    Multimap<Expression2, CcgParse> lfParseMap = ArrayListMultimap.create();
    for (CcgParse conditionalParse : conditionalParses) {
      Expression2 lf = simplifier.apply(conditionalParse.getLogicalForm());
      lfParseMap.put(lf, conditionalParse);
    }

    IncEval eval = model.getEval();
    Multimap<Expression2, IncEvalState> unconditionalEvaluations = ArrayListMultimap.create();
    Multimap<Expression2, IncEvalState> conditionalEvaluations = ArrayListMultimap.create();
    Map<Expression2, Double> evaluationPartitionFunctions = Maps.newHashMap();
    for (Expression2 lf : lfParseMap.keySet()) {
      System.out.println(lf);

      List<IncEvalState> conditionalStates = null;
      List<IncEvalState> unconditionalStates = null;
      if (example.getLabelCost() != null) {
        conditionalStates = eval.evaluateBeam(lf, diagram, example.getLabelCost(), evalBeamSize);
        
        if (conditionalStates.size() > 0) {
          unconditionalStates = eval.evaluateBeam(lf, diagram, evalBeamSize);
        }
      } else {
        unconditionalStates = eval.evaluateBeam(lf, diagram, evalBeamSize);
        conditionalStates = unconditionalStates;
      }
      
      List<IncEvalState> denotationCorrectStates = Lists.newArrayList();
      for (IncEvalState conditionalState : conditionalStates) {
        if (example.isCorrectDenotation(conditionalState.getDenotation(), conditionalState.getDiagram())) {
          denotationCorrectStates.add(conditionalState);
          System.out.println("  " + conditionalState.getDenotation() + " " + lf + " " + conditionalState.getDiagram());
        }
      }

      if (denotationCorrectStates.size() > 0) {
        unconditionalEvaluations.putAll(lf, unconditionalStates);
        conditionalEvaluations.putAll(lf, denotationCorrectStates);
        evaluationPartitionFunctions.put(lf, getPartitionFunction(unconditionalStates));
      }
    }
    
    if (conditionalEvaluations.size() == 0) {
      // Search error.
      throw new ZeroProbabilityError();
    }
    
    // Compute the normalization for the correct side of the
    // gradient updates.
    double correctProbability = 0.0;
    for (Expression2 lf : conditionalEvaluations.keySet()) {
      correctProbability += (getParsePartitionFunction(lfParseMap.get(lf)) / parserPartitionFunction)
          * (getPartitionFunction(conditionalEvaluations.get(lf)) / evaluationPartitionFunctions.get(lf));
    }
    
    // Compute parser gradient
    for (Expression2 lf : conditionalEvaluations.keySet()) {
      double evalProb = getPartitionFunction(conditionalEvaluations.get(lf)) / evaluationPartitionFunctions.get(lf);
      
      for (CcgParse parse : lfParseMap.get(lf)) {
        double prob = (parse.getSubtreeProbability() / parserPartitionFunction) * evalProb / correctProbability;
        family.incrementParserStatistics(gradient, currentParameters, sentence, parse, prob);
      }
    }

    for (CcgParse unconditionalParse : unconditionalParses) {
      double prob = unconditionalParse.getSubtreeProbability() / parserPartitionFunction;
      family.incrementParserStatistics(gradient, currentParameters, sentence, 
          unconditionalParse, -1 * prob);
    }
    
    // Compute eval gradient
    for (Expression2 lf : conditionalEvaluations.keySet()) {
      double parserProb = getParsePartitionFunction(lfParseMap.get(lf)) / parserPartitionFunction;
      double lfPartitionFunction = evaluationPartitionFunctions.get(lf);
      
      for (IncEvalState state : conditionalEvaluations.get(lf)) {
        double prob = (state.getProb() / lfPartitionFunction) * parserProb / correctProbability; 
        family.incrementEvalStatistics(gradient, currentParameters, lf, state, prob);
      }
      
      double lfWeight = (parserProb * getPartitionFunction(conditionalEvaluations.get(lf))
          / evaluationPartitionFunctions.get(lf)) / correctProbability;
      for (IncEvalState state : unconditionalEvaluations.get(lf)) {
        double prob = state.getProb() / lfPartitionFunction;
        family.incrementEvalStatistics(gradient, currentParameters, lf, state, -1 * prob * lfWeight);
      }
    }
    
    // Compute loglikelihood

    return Math.log(correctProbability);
  }
  
  private double getPartitionFunction(Iterable<IncEvalState> states) {
    double partitionFunction = 0.0;
    for (IncEvalState state : states) {
      partitionFunction += state.getProb();
    }
    return partitionFunction;
  }
  
  private double getParsePartitionFunction(Iterable<CcgParse> parses) {
    double partitionFunction = 0.0;
    for (CcgParse parse : parses) {
      partitionFunction += parse.getSubtreeProbability();
    }
    return partitionFunction;
  }

}
