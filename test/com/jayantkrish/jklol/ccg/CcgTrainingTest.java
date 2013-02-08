package com.jayantkrish.jklol.ccg;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.DefaultLogFunction;
import com.jayantkrish.jklol.training.StochasticGradientTrainer;

/**
 * Regression tests for training CCG parsers from dependency
 * structures and syntactic trees. This test encompasses
 * {@link CcgParser}, {@link ParametricCcgParser},
 * {@link CcgLoglikelihoodOracle} and {@link CcgPerceptronOracle}.
 * 
 * @author jayant
 */
public class CcgTrainingTest extends TestCase {

  private static final String[] lexicon = {
      "block,N{0},,0 pred:block", "object,N{0},,0 pred:object",
      "red,(N{1}/N{1}){0},,0 pred:red,pred:red 1 1", "green,(N{1}/N{1}){0},,0 pred:green,pred:green 1 1",
      "green,N{0},,0 pred:green", "the,(N{1}/N{1}){0},,0 the", "a,(N{1}/N{1}){0},,0 the",
      "near,((N{1}\\N{1}){0}/N{2}){0},,0 pred:close,pred:close 1 1,pred:close 2 2",
      "near,((N{1}\\N{1}){0}/N{2}){0},,0 pred:near,pred:near 1 1,pred:near 2 2",
      "near,((S{1}/(S{1}\\N{0}){1}){0}/N{2}){0},,0 pred:near,pred:near 2 2",
      "near,(PP{0}/N{1}){0},,0 pred:near,pred:near 2 1",
      "kinda,((N{1}/N{1}){2}/(N{1}/N{1}){2}){0},,0 pred:almost,pred:almost 1 2",
      "is,((S{0}\\N{1}){0}/N{2}){0},,0 pred:equals,pred:equals 1 1,pred:equals 2 2",
      "\",\",((N{1}\\N{1}){0}/N{2}){0},,\"0 ,\",\", 1 1\",\", 2 2\"",
      "2,N{0},,0 NUM", "2,(N{1}/N{1}){0},,0 NUM,NUM 1 1",
      "\"#\",(N{1}/N{1}){0},,0 #,# 1 1", "\"#\",((N{1}/N{1}){2}/(N{1}/N{1}){2}){0},,0 #,# 1 2",
      "foo,ABC{0},,0 foo", "foo,ABCD{0},,0 foo",
      "UNK-JJ,(N{1}/N{1}){0},,0 pred:unk-jj,pred:unk-jj 1 1",
      "UNK-JJ,N{0},,0 pred:unk-jj",
      "UNK-JJ,(PP{1}/N{1}){0},,0 pred:unk-jj,pred:unk-jj 1 1",
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

  private static final String[] trainingDataWithSyntax = {
    "the block is green###pred:equals 2 1 pred:block 1,pred:equals 2 2 pred:green 3###<S <N <(N/N) DT the> <N NN block>> <(S\\N) <(S\\N)/N VB is> <N NN green>>>",
    "red block###pred:red 0 1 pred:block 1###<N <(N/N) JJ red> <N NN block>>",
    "red green block###pred:red 0 1 pred:block 2,pred:green 1 1 pred:block 2###<N <(N/N) JJ red> <N <(N/N) JJ green> <N NN block>>>",
    "red block near the green block###pred:red 0 1 pred:block 1,pred:green 4 1 pred:block 5,pred:near 2 1 pred:block 1,pred:near 2 2 pred:block 5###"
        + "<N <N <(N/N) JJ red> <N NN block>> <N\\N <(N\\N)/N IN near> <N <N/N DT the> <N <(N/N) JJ green> <N NN block>>>>>",
    "# 2 block###\"# 0 1 NUM 1\",\"NUM 1 1 pred:block 2\"###<N <N/N <((N/N)/(N/N)) JJ #> <(N/N) JJ 2>> <N NN block>>",
    "foo######<ABCD NN foo>",
    "block######<N NN block>",
    "not_in_lexicon block###pred:unk-jj 0 1 pred:block 1###<N <(N/N) JJ not_in_lexicon> <N NN block>>"
  };
  
  private static final String[] ruleArray = {"N{0} (S{1}/(S{1}\\N{0}){1}){1}", "ABC{0} ABCD{0}"};

  private ParametricCcgParser family;
  private List<CcgExample> trainingExamples;
  private List<CcgExample> trainingExamplesWithSyntax;
  private List<CcgExample> trainingExamplesSyntaxOnly;
  private Set<String> posTags;

  private static final double TOLERANCE = 1e-10;

  public void setUp() {
    trainingExamples = Lists.newArrayList();
    for (int i = 0; i < trainingData.length; i++) {
      trainingExamples.add(CcgExample.parseFromString(trainingData[i], false));
    }

    trainingExamplesWithSyntax = Lists.newArrayList();
    for (int i = 0; i < trainingDataWithSyntax.length; i++) {
      trainingExamplesWithSyntax.add(CcgExample.parseFromString(trainingDataWithSyntax[i], false));
    }
    posTags = CcgExample.getPosTagVocabulary(trainingExamplesWithSyntax);
    posTags.add(ParametricCcgParser.DEFAULT_POS_TAG);

    trainingExamplesSyntaxOnly = Lists.newArrayList();
    for (CcgExample syntaxExample : trainingExamplesWithSyntax) {
      trainingExamplesSyntaxOnly.add(new CcgExample(syntaxExample.getWords(), syntaxExample.getPosTags(),
          null, syntaxExample.getSyntacticParse()));
    }
    
    family = ParametricCcgParser.parseFromLexicon(Arrays.asList(lexicon), Arrays.asList(ruleArray),
        null, posTags, true);
  }

  public void testParseFromLexicon() {
    CcgParser parser = family.getModelFromParameters(family.getNewSufficientStatistics());
    List<CcgParse> parses = parser.beamSearch(Arrays.asList("block"), 10);
    assertEquals(2, parses.size());

    parses = parser.beamSearch(Arrays.asList("near"), 10);
    assertEquals(4, parses.size());

    parses = parser.beamSearch(Arrays.asList(","), 10);
    assertEquals(1, parses.size());

    parses = parser.beamSearch(Arrays.asList("#"), 10);
    assertEquals(2, parses.size());

    parses = parser.beamSearch(Arrays.asList("#", "2", "block"), 10);
    assertEquals(8, parses.size());
  }

  public void testTrainLoglikelihoodDependenciesOnly() {
    CcgParser parser = trainLoglikelihoodParser(trainingExamples);
    assertZeroDependencyError(parser, trainingExamples);
    // Check that the resulting parameters are sensible.
    assertEquals(1.0, parser.beamSearch(Arrays.asList("red"), 10).get(0).getSubtreeProbability(), 0.000001);
  }

  public void testTrainLoglikelihoodWithSyntax() {
    CcgParser parser = trainLoglikelihoodParser(trainingExamplesWithSyntax);
    assertZeroDependencyError(parser, trainingExamplesWithSyntax);
    assertTrainedParserUsesSyntax(parser);
  }

  public void testTrainLoglikelihoodSyntaxOnly() {
    CcgParser parser = trainLoglikelihoodParser(trainingExamplesSyntaxOnly);
    assertTrainedParserUsesSyntax(parser);

    List<CcgParse> parses = filterNonAtomicParses(parser.beamSearch(
        Arrays.asList("object", "near", "block"), 10));
    // The two parses differ only in the semantics of near, which is
    // unconstrained in the training data.
    assertEquals(2, parses.size());
    assertEquals(parses.get(0).getSubtreeProbability(), parses.get(1).getSubtreeProbability(), TOLERANCE);
  }

  public void testTrainPerceptronWithSyntax() {
    CcgParser parser = trainPerceptronParser(trainingExamplesWithSyntax);
    assertZeroDependencyError(parser, trainingExamplesWithSyntax);
    assertTrainedParserUsesSyntax(parser);
  }

  public void testTrainPerceptronSyntaxOnly() {
    CcgParser parser = trainPerceptronParser(trainingExamplesSyntaxOnly);
    assertTrainedParserUsesSyntax(parser);
  }

  private CcgParser trainLoglikelihoodParser(List<CcgExample> examples) {
    CcgLoglikelihoodOracle oracle = new CcgLoglikelihoodOracle(family, 100);
    StochasticGradientTrainer trainer = StochasticGradientTrainer.createWithL2Regularization(10, 1, 1,
        true, 0.1, new DefaultLogFunction());

    SufficientStatistics parameters = trainer.train(oracle, oracle.initializeGradient(), examples);
    CcgParser parser = family.getModelFromParameters(parameters);
    System.out.println(family.getParameterDescription(parameters));
    return parser;
  }

  private CcgParser trainPerceptronParser(List<CcgExample> examples) {
    CcgPerceptronOracle oracle = new CcgPerceptronOracle(family, 100);
    StochasticGradientTrainer trainer = StochasticGradientTrainer.createWithL2Regularization(21, 1, 1,
        false, 0.0, new DefaultLogFunction());

    SufficientStatistics initialParameters = oracle.initializeGradient();
    SufficientStatistics parameters = trainer.train(oracle, initialParameters, examples);
    CcgParser parser = family.getModelFromParameters(parameters);
    System.out.println(family.getParameterDescription(parameters));
    return parser;
  }

  private void assertTrainedParserUsesSyntax(CcgParser parser) {
    List<CcgParse> parses = filterNonAtomicParses(parser.beamSearch(Arrays.asList("the", "red", "block"), 
        Arrays.asList("DT", "NN", "NN"), 10));

    // Check that syntactic information is being used in the learned
    // parser.
    assertEquals(3, parses.size());
    CcgParse bestParse = parses.get(0);
    // Best parse should be <N <N/N> <N <N/N> <N>>>
    assertTrue(bestParse.getLeft().isTerminal());
    assertTrue(bestParse.getSubtreeProbability() > parses.get(1).getSubtreeProbability() + 0.000001);
    
    // Check that weights are being learned for unary rules.
    parses = parser.beamSearch(Arrays.asList("foo"), Arrays.asList("NN"), 100);
    assertEquals(3, parses.size());
    System.out.println(parses);
    assertNull(parses.get(0).getUnaryRule());
    assertEquals("ABCD", parses.get(0).getSyntacticCategory().getValue());
    assertTrue(parses.get(0).getSubtreeProbability() > parses.get(1).getSubtreeProbability() + 0.000001);

    parses = parser.beamSearch(Arrays.asList("block"), Arrays.asList("NN"), 100);
    assertEquals(2, parses.size());
    for (CcgParse parse : parses) {
      System.out.println(parse.getSubtreeProbability() + " " + parse);
    }
    assertTrue(parses.get(0).getSyntacticCategory().isAtomic());
    assertTrue(parses.get(0).getSubtreeProbability() > parses.get(1).getSubtreeProbability() + 0.000001);
    
    // Check that backoff to POS tags works properly.
    parses = parser.beamSearch(Arrays.asList("another_new_word", "block"), Arrays.asList("JJ", "NN"), 100);
    for (CcgParse parse : parses) {
      System.out.println(parse.getSubtreeProbability() + " " + parse);
    }
    assertEquals(3, parses.size());
    assertTrue(parses.get(0).getSyntacticCategory().equals(SyntacticCategory.parseFrom("N")));
  }

  private void assertZeroDependencyError(CcgParser parser, Iterable<CcgExample> examples) {
    // Test that zero training error is achieved.
    for (CcgExample example : examples) {
      List<CcgParse> parses = parser.beamSearch(example.getWords(), example.getPosTags(), 100);
      CcgParse bestParse = parses.get(0);

      System.out.println(example.getWords() + " " + bestParse);
      System.out.println(example.getDependencies());
      System.out.println(bestParse.getAllDependencies());
      assertEquals(example.getDependencies(), Sets.newHashSet(bestParse.getAllDependencies()));
    }
  }
  
  private List<CcgParse> filterNonAtomicParses(List<CcgParse> parses) {
    List<CcgParse> atomicParses = Lists.newArrayList();
    for (CcgParse parse : parses) {
      if (parse.getSyntacticCategory().isAtomic()) {
        atomicParses.add(parse);
      }
    }
    return atomicParses;
  }
}
