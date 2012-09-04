package com.jayantkrish.jklol.ccg;

import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.DefaultLogFunction;
import com.jayantkrish.jklol.training.StochasticGradientTrainer;

/**
 * Regression tests for training CCG parsers from dependency structures.
 * This test encompasses {@link CcgParser}, {@link CcgLoglikelihoodOracle} 
 * and {@link ParametricCcgParser}.
 *  
 * @author jayant
 */
public class CcgTrainingTest extends TestCase {

  private static final String[] lexicon = {"block,pred:block,N", "object,pred:object,N", 
    "red,pred:red,N/>N,pred:red 1 ?1","green,pred:green,N/>N,pred:green 1 ?1",
    "the,the,N/>N","a,the,N/>N",
    "near,pred:near,(N\\>N)/N,pred:near 1 ?1#pred:near 2 ?2", 
    "near,pred:close,(N\\>N)/N,pred:close 1 ?1#pred:close 2 ?2", "near,pred:near,PP/>N,",
    "kinda,pred:almost,(N/>N)/>(N/>N),pred:almost 1 ?2#?2 1 ?1"};

  private static final String[] trainingData = {
    "red block###pred:red 1 pred:block",
    "red green block###pred:red 1 pred:block#pred:green 1 pred:block",
    "red object near the green block###pred:red 1 pred:object#pred:green 1 pred:block#pred:near 1 pred:object#pred:near 2 pred:block",
    "the kinda red block###pred:red 1 pred:block#pred:almost 1 pred:red"
  };

  private ParametricCcgParser family;
  private List<CcgExample> trainingExamples;

  public void setUp() {
    family = ParametricCcgParser.parseFromLexicon(Arrays.asList(lexicon));
    
    trainingExamples = Lists.newArrayList();
    for (int i = 0; i < trainingData.length; i++) {
      trainingExamples.add(CcgExample.parseFromString(trainingData[i]));
    }
  }

  public void testParseFromLexicon() {
    CcgParser parser = family.getParserFromParameters(family.getNewSufficientStatistics());
    List<CcgParse> parses = parser.beamSearch(Arrays.asList("block"), 10);
    assertEquals(1, parses.size());
    
    parses = parser.beamSearch(Arrays.asList("near"), 10);
    assertEquals(3, parses.size());
    System.out.println(parses);
  }

  public void testTrain() {
    CcgLoglikelihoodOracle oracle = new CcgLoglikelihoodOracle(family, 10);
    StochasticGradientTrainer trainer = StochasticGradientTrainer.createWithL2Regularization(10, 1, 1, 
        true, 0.1, new DefaultLogFunction());
    
    SufficientStatistics parameters = trainer.train(oracle, oracle.initializeGradient(), trainingExamples);
    CcgParser parser = family.getParserFromParameters(parameters);

    // Test that zero training error is achieved.
    for (CcgExample example : trainingExamples) {
      List<CcgParse> parses = parser.beamSearch(example.getWords(), 10);
      System.out.println(example.getWords() + " " + parses.get(0).getAllDependencies());
      assertEquals(example.getDependencies(), Sets.newHashSet(parses.get(0).getAllDependencies()));
    }
  }
}
