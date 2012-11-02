package com.jayantkrish.jklol.ccg;

import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
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

  private static final String[] lexicon = {
    "block,pred:block,N", "object,pred:object,N", 
    "red,pred:red,N/>N,pred:red 1 ?1","green,pred:green,N/>N,pred:green 1 ?1",
    "the,the,N/>N","a,the,N/>N",
    "near,pred:near,(N\\\\>N)/N,pred:near 1 ?1#pred:near 2 ?2",
    "near,pred:near,(S/(S\\\\N))/N,pred:near 2 ?2#pred:near 1 pred:block#?1 1 pred:block",
    "near,pred:close,(N\\\\>N)/N,pred:close 1 ?1#pred:close 2 ?2", "near,pred:near,PP/>N,",
    "kinda,pred:almost,(N/>N)/>(N/>N),pred:almost 1 ?2#?2 1 ?1",
    "is,pred:equals,(S\\\\N)/N,pred:equals 1 ?1#pred:equals 2 ?2",
    "\",\",\",\",(N\\\\>N)/N,\", 1 ?1#, 2 ?2\"",
    "2,NUM,N"
  };

  private static final String[] trainingData = {
    "red block###pred:red 0 1 pred:block 1",
    "red green block###pred:red 0 1 pred:block 2#pred:green 1 1 pred:block 2",
    "red object near the green block###pred:red 0 1 pred:object 1#pred:green 4 1 pred:block 5#pred:near 2 1 pred:object 1#pred:near 2 2 pred:block 5",
    "red block near the green block###pred:red 0 1 pred:block 1#pred:green 4 1 pred:block 5#pred:near 2 1 pred:block 1#pred:near 2 2 pred:block 5",
    "the kinda red block###pred:red 2 1 pred:block 3#pred:almost 1 1 pred:red 2",
    "near the object is the red block###pred:near 0 2 pred:object 2#pred:near 0 1 pred:block 0#pred:equals 3 1 pred:block 0#pred:equals 3 2 pred:block 6#pred:red 5 1 pred:block 6",
    "block , object###\\, 1 1 pred:block 0#\\, 1 2 pred:object 2",
  };
  
  private static final String[] trainingDataWithLexicon = {
    "red block###pred:red 0 1 pred:block 1###red,pred:red,N/>N,pred:red 1 ?1@@@block,pred:block,N",
    "red green block###pred:red 0 1 pred:block 2#pred:green 1 1 pred:block 2###red,pred:red,N/>N,pred:red 1 ?1@@@green,pred:green,N/>N,pred:green 1 ?1@@@block,pred:block,N",
    "red block near the green block###pred:red 0 1 pred:block 1#pred:green 4 1 pred:block 5#pred:near 2 1 pred:block 1#pred:near 2 2 pred:block 5###"
    + "red,pred:red,N/>N,pred:red 1 ?1@@@block,pred:block,N@@@near,pred:near,(N\\\\>N)/N,pred:near 1 ?1#pred:near 2 ?2@@@the,the,N/>N@@@green,pred:green,N/>N,pred:green 1 ?1@@@block,pred:block,N",
  };

  private ParametricCcgParser family;
  private List<CcgExample> trainingExamples;
  private List<CcgExample> trainingExamplesWithLexicon;

  public void setUp() {
    family = ParametricCcgParser.parseFromLexicon(Arrays.asList(lexicon),
        Lists.<String>newArrayList());
    
    trainingExamples = Lists.newArrayList();
    for (int i = 0; i < trainingData.length; i++) {
      trainingExamples.add(CcgExample.parseFromString(trainingData[i]));
    }
    
    trainingExamplesWithLexicon = Lists.newArrayList();
    for (int i = 0; i < trainingDataWithLexicon.length; i++) {
      trainingExamplesWithLexicon.add(CcgExample.parseFromString(trainingDataWithLexicon[i]));
    }
  }

  public void testParseFromLexicon() {
    CcgParser parser = family.getParserFromParameters(family.getNewSufficientStatistics());
    List<CcgParse> parses = parser.beamSearch(Arrays.asList("block"), 10);
    assertEquals(1, parses.size());
    
    parses = parser.beamSearch(Arrays.asList("near"), 10);
    assertEquals(4, parses.size());
    
    parses = parser.beamSearch(Arrays.asList(","), 10);
    assertEquals(1, parses.size());
  }

  public void testTrain() {
    CcgParser parser = testZeroTrainingError(trainingExamples);
    // Check that the resulting parameters are sensible.  
    assertEquals(1.0, parser.beamSearch(Arrays.asList("red"), 10).get(0).getSubtreeProbability(), 0.000001);
  }

  public void testTrainWithLexicon() {
    testZeroTrainingError(trainingExamplesWithLexicon);
  }
  
  private CcgParser testZeroTrainingError(List<CcgExample> examples) {
    CcgLoglikelihoodOracle oracle = new CcgLoglikelihoodOracle(family, 10);
    StochasticGradientTrainer trainer = StochasticGradientTrainer.createWithL2Regularization(10, 1, 1, 
        true, 0.1, new DefaultLogFunction());
    
    SufficientStatistics parameters = trainer.train(oracle, oracle.initializeGradient(), examples);
    CcgParser parser = family.getParserFromParameters(parameters);
    System.out.println(family.getParameterDescription(parameters));

    // Test that zero training error is achieved.
    for (CcgExample example : examples) {
      List<CcgParse> parses = parser.beamSearch(example.getWords(), 10);
      System.out.println(example.getWords() + " " + parses.get(0).getAllDependencies());
      assertEquals(example.getDependencies(), Sets.newHashSet(parses.get(0).getAllDependencies()));
    }
    
    return parser; 
  }
}

