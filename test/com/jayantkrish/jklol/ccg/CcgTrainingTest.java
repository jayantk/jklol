package com.jayantkrish.jklol.ccg;

import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.DefaultLogFunction;
import com.jayantkrish.jklol.training.StochasticGradientTrainer;
import com.jayantkrish.jklol.util.Pseudorandom;

/**
 * Regression tests for training CCG parsers from dependency structures.
 * This test encompasses {@link CcgParser}, {@link CcgLoglikelihoodOracle} 
 * and {@link ParametricCcgParser}.
 *  
 * @author jayant
 */
public class CcgTrainingTest extends TestCase {

  private static final String[] lexicon = {
    "block,N{0},0 pred:block", "object,N{0},0 pred:object", 
    "red,(N{1}/N{1}){0},0 pred:red,pred:red 1 1","green,(N{1}/N{1}){0},0 pred:green,pred:green 1 1",
    "the,(N{1}/N{1}){0},0 the","a,(N{1}/N{1}){0},0 the",
    "near,((N{1}\\N{1}){0}/N{2}){0},0 pred:close,pred:close 1 1,pred:close 2 2", 
    "near,((N{1}\\N{1}){0}/N{2}){0},0 pred:near,pred:near 1 1,pred:near 2 2",
    "near,((S{1}/(S{1}\\N{0}){1}){0}/N{2}){0},0 pred:near,pred:near 2 2",
    "near,(PP{0}/N{1}){0},0 pred:near,pred:near 2 1",
    "kinda,((N{1}/N{1}){2}/(N{1}/N{1}){2}){0},0 pred:almost,pred:almost 1 2",
    "is,((S{0}\\N{1}){0}/N{2}){0},0 pred:equals,pred:equals 1 1,pred:equals 2 2",
    "\",\",((N{1}\\N{1}){0}/N{2}){0},\"0 ,\",\", 1 1\",\", 2 2\"",
    "2,N{0},0 NUM", "2,(N{1}/N{1}){0},0 NUM,NUM 1 1",
    "\"#\",(N{1}/N{1}){0},0 #,# 1 1", "\"#\",((N{1}/N{1}){2}/(N{1}/N{1}){2}){0},0 #,# 1 2" 
  };

  private static final String[] trainingData = {
    "red block###pred:red 0 1 pred:block 1",
    "red green block###pred:red 0 1 pred:block 2,pred:green 1 1 pred:block 2",
    "red object near the green block###pred:red 0 1 pred:object 1,pred:green 4 1 pred:block 5,pred:near 2 1 pred:object 1,pred:near 2 2 pred:block 5",
    "red block near the green block###pred:red 0 1 pred:block 1,pred:green 4 1 pred:block 5,pred:near 2 1 pred:block 1,pred:near 2 2 pred:block 5",
    "the kinda red block###pred:red 2 1 pred:block 3,pred:almost 1 1 pred:red 2",
    "near the object is the red block###pred:near 0 2 pred:object 2,pred:equals 3 1 pred:near 0,pred:equals 3 2 pred:block 6,pred:red 5 1 pred:block 6",
    "block , object###\", 1 1 pred:block 0\",\", 1 2 pred:object 2\"",
  };
  
  private static final String[] trainingDataWithLexicon = {
    "red block###pred:red 0 1 pred:block 1###\"red\",\"(N{1}/N{1}){0}\",\"0 pred:red\",\"pred:red 1 1\"@@@\"block\",\"N{0}\",\"0 pred:block\"",
    "red green block###pred:red 0 1 pred:block 2,pred:green 1 1 pred:block 2###red,(N{1}/N{1}){0},0 pred:red,pred:red 1 1@@@green,(N{1}/N{1}){0},0 pred:green,pred:green 1 1@@@block,N{0},0 pred:block",
    "red block near the green block###pred:red 0 1 pred:block 1,pred:green 4 1 pred:block 5,pred:near 2 1 pred:block 1,pred:near 2 2 pred:block 5###"
    + "red,(N{1}/N{1}){0},0 pred:red,pred:red 1 1@@@block,N{0},0 pred:block@@@near,((N{1}\\N{1}){0}/N{2}){0},0 pred:near,pred:near 1 1,pred:near 2 2@@@the,(N{1}/N{1}){0},0 the@@@green,(N{1}/N{1}){0},0 pred:green,pred:green 1 1@@@block,N{0},0 pred:block",
    "# 2 block###\"# 0 1 NUM 1\",\"NUM 1 1 pred:block 2\"###\"#\",\"((N{1}/N{1}){2}/(N{1}/N{1}){2}){0}\",\"0 #\",\"# 1 2\"@@@2,(N{1}/N{1}){0},0 NUM,NUM 1 1@@@block,N{0},0 pred:block"
  };

  private ParametricCcgParser family;
  private List<CcgExample> trainingExamples;
  private List<CcgExample> trainingExamplesWithLexicon;

  public void setUp() {
    family = ParametricCcgParser.parseFromLexicon(Arrays.asList(lexicon),
        Lists.<String>newArrayList(), null, true);

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
    CcgParser parser = family.getModelFromParameters(family.getNewSufficientStatistics());
    List<CcgParse> parses = parser.beamSearch(Arrays.asList("block"), 10);
    assertEquals(1, parses.size());
    
    parses = parser.beamSearch(Arrays.asList("near"), 10);
    assertEquals(4, parses.size());
    
    parses = parser.beamSearch(Arrays.asList(","), 10);
    assertEquals(1, parses.size());
    
    parses = parser.beamSearch(Arrays.asList("#"), 10);
    assertEquals(2, parses.size());
    
    parses = parser.beamSearch(Arrays.asList("#", "2", "block"), 10);
    assertEquals(4, parses.size());
  }

  public void testTrain() {
    CcgParser parser = testZeroTrainingError(trainingExamples);
    // Check that the resulting parameters are sensible.  
    assertEquals(1.0, parser.beamSearch(Arrays.asList("red"), 10).get(0).getSubtreeProbability(), 0.000001);
  }

  public void testTrainWithLexicon() {
    testZeroTrainingError(trainingExamplesWithLexicon);
  }
  
  public void testTrainPerceptronWithLexicon() {
    CcgPerceptronOracle oracle = new CcgPerceptronOracle(family, 100);
    StochasticGradientTrainer trainer = StochasticGradientTrainer.createWithL2Regularization(10, 1, 1, 
        false, 0.0, new DefaultLogFunction());

    // Ensure that this test is deterministic.
    Pseudorandom.get().setSeed(1);
    SufficientStatistics initialParameters = oracle.initializeGradient();
    initialParameters.perturb(0.01);
    
    SufficientStatistics parameters = trainer.train(oracle, initialParameters, 
        trainingExamplesWithLexicon);
    CcgParser parser = family.getModelFromParameters(parameters);
    System.out.println(family.getParameterDescription(parameters));

    assertZeroError(parser, trainingExamplesWithLexicon);
  }
  
  private CcgParser testZeroTrainingError(List<CcgExample> examples) {
    CcgLoglikelihoodOracle oracle = new CcgLoglikelihoodOracle(family, 100);
    StochasticGradientTrainer trainer = StochasticGradientTrainer.createWithL2Regularization(10, 1, 1, 
        true, 0.1, new DefaultLogFunction());

    SufficientStatistics parameters = trainer.train(oracle, oracle.initializeGradient(), examples);
    CcgParser parser = family.getModelFromParameters(parameters);
    System.out.println(family.getParameterDescription(parameters));

    assertZeroError(parser, examples);
    return parser; 
  }
  
  private void assertZeroError(CcgParser parser, Iterable<CcgExample> examples) {
    // Test that zero training error is achieved.
    for (CcgExample example : examples) {
      List<CcgParse> parses = parser.beamSearch(example.getWords(), 100);
      CcgParse bestParse = null;
      for (CcgParse parse : parses) {
        if (parse.getSyntacticCategory().isAtomic()) {
          bestParse = parse;
          break;
        }
      }

      System.out.println(example.getWords() + " " + bestParse);
      assertEquals(example.getDependencies(), Sets.newHashSet(bestParse.getAllDependencies()));
    }
  }
}

