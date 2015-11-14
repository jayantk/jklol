package com.jayantkrish.jklol.models.loglinear;

import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.tensor.Tensor;

public class BackoffLogLinearFactorTest extends TestCase {
  
  private VariableNumMap wordVar, lemmaVar, posVar, basePosVar; 
  
  private DiscreteFactor wordToLemma, posToBase; 

  private static final String[] wordArray = {
    "the", "dog", "dogs", "cat", "cats", "SKIP", "temporary"
  };
  
  private static final String[] lemmaArray = {
    "th", "dog", "cat", "FOO", "BAR", "temp"
  };
  
  private static final String[] posArray = {
    "DT", "NN", "NNS"
  };
  
  private static final String[] basePosArray = {
    "DT", "NN"
  };
  
  private static final double TOLERANCE = 1e-6; 
  
  public void setUp() {
    DiscreteVariable words = new DiscreteVariable("words", Arrays.asList(wordArray));
    DiscreteVariable lemmas = new DiscreteVariable("lemmas", Arrays.asList(lemmaArray));
    DiscreteVariable pos = new DiscreteVariable("pos", Arrays.asList(posArray));
    DiscreteVariable basePos = new DiscreteVariable("basePos", Arrays.asList(basePosArray));
    
    wordVar = VariableNumMap.singleton(0, "words", words);
    lemmaVar = VariableNumMap.singleton(1, "lemmas", lemmas);
    posVar = VariableNumMap.singleton(2, "pos", pos);
    basePosVar = VariableNumMap.singleton(3, "basePos", basePos);

    TableFactorBuilder builder = new TableFactorBuilder(wordVar.union(lemmaVar),
        SparseTensorBuilder.getFactory());
    builder.setWeight(1.0, "the", "th");
    builder.setWeight(1.0, "the", "FOO");
    builder.setWeight(1.0, "the", "BAR");
    builder.setWeight(1.0, "dog", "dog");
    builder.setWeight(1.0, "dogs", "dog");
    builder.setWeight(1.0, "cat", "cat");
    builder.setWeight(1.0, "cats", "cat");
    builder.setWeight(1.0, "temporary", "temp");
    wordToLemma = builder.build();
    
    builder = new TableFactorBuilder(posVar.union(basePosVar), SparseTensorBuilder.getFactory());
    builder.setWeight(1.0, "DT", "DT");
    builder.setWeight(1.0, "NN", "NN");
    builder.setWeight(1.0, "NNS", "NN");
    posToBase = builder.build();
  }
  
  public void testOneVariable() {
    IndicatorLogLinearFactor lemmaFactor = IndicatorLogLinearFactor.createDenseFactor(lemmaVar);

    List<Tensor> maps = Lists.newArrayList(wordToLemma.getWeights());
    BackoffLogLinearFactor factor = new BackoffLogLinearFactor(wordVar, lemmaFactor, maps);
    
    SufficientStatistics stats = factor.getNewSufficientStatistics();
    SufficientStatistics currentParameters = factor.getNewSufficientStatistics();

    factor.incrementSufficientStatisticsFromAssignment(stats, currentParameters,
        wordVar.outcomeArrayToAssignment("dog"), 1.0);
    factor.incrementSufficientStatisticsFromAssignment(stats, currentParameters,
        wordVar.outcomeArrayToAssignment("SKIP"), 1.0);
    factor.incrementSufficientStatisticsFromAssignment(stats, currentParameters,
        wordVar.outcomeArrayToAssignment("the"), 1.0);
    factor.incrementSufficientStatisticsFromAssignment(stats, currentParameters,
        wordVar.outcomeArrayToAssignment("cat"), 2.0);
    factor.incrementSufficientStatisticsFromAssignment(stats, currentParameters,
        wordVar.outcomeArrayToAssignment("cats"), -1.0);
    
    DiscreteFactor t = factor.getModelFromParameters(stats).coerceToDiscrete();
    assertEquals(0.0, t.getUnnormalizedLogProbability("temporary"), TOLERANCE);
    assertEquals(1.0, t.getUnnormalizedLogProbability("dog"), TOLERANCE);
    assertEquals(1.0, t.getUnnormalizedLogProbability("dogs"), TOLERANCE);
    assertEquals(1.0, t.getUnnormalizedLogProbability("cats"), TOLERANCE);
    assertEquals(3.0, t.getUnnormalizedLogProbability("the"), TOLERANCE);
    assertEquals(0, t.getUnnormalizedLogProbability("SKIP"), TOLERANCE);
  }
  
  public void testTwoVariable() {
    IndicatorLogLinearFactor lemmaFactor = IndicatorLogLinearFactor
        .createDenseFactor(lemmaVar.union(basePosVar));

    List<Tensor> maps = Lists.newArrayList(wordToLemma.getWeights(), posToBase.getWeights());
    VariableNumMap vars = wordVar.union(posVar);
    BackoffLogLinearFactor factor = new BackoffLogLinearFactor(vars, lemmaFactor, maps);
    
    SufficientStatistics stats = factor.getNewSufficientStatistics();
    SufficientStatistics currentParameters = factor.getNewSufficientStatistics();
    
    factor.incrementSufficientStatisticsFromAssignment(stats, currentParameters,
        vars.outcomeArrayToAssignment("dog", "NN"), 1.0);
    
    DiscreteFactor t = factor.getModelFromParameters(stats).coerceToDiscrete();
    assertEquals(1.0, t.getUnnormalizedLogProbability("dog", "NN"), TOLERANCE);
    assertEquals(1.0, t.getUnnormalizedLogProbability("dog", "NNS"), TOLERANCE);
    assertEquals(1.0, t.getUnnormalizedLogProbability("dogs", "NN"), TOLERANCE);
    assertEquals(1.0, t.getUnnormalizedLogProbability("dogs", "NNS"), TOLERANCE);
  }
}
