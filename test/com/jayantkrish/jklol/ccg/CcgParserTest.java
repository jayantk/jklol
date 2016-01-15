package com.jayantkrish.jklol.ccg;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.io.NullOutputStream;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.ccg.chart.ChartCost;
import com.jayantkrish.jklol.ccg.chart.ChartEntry;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.lambda2.LambdaApplicationReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.VariableCanonicalizationReplacementRule;
import com.jayantkrish.jklol.ccg.lexicon.CcgLexicon;
import com.jayantkrish.jklol.ccg.lexicon.LexiconScorer;
import com.jayantkrish.jklol.ccg.lexicon.SkipLexicon;
import com.jayantkrish.jklol.ccg.lexicon.SkipLexicon.SkipTrigger;
import com.jayantkrish.jklol.ccg.lexicon.StringLexicon;
import com.jayantkrish.jklol.ccg.lexicon.StringLexicon.CategorySpanConfig;
import com.jayantkrish.jklol.ccg.lexicon.SyntaxLexiconScorer;
import com.jayantkrish.jklol.ccg.lexicon.TableLexicon;
import com.jayantkrish.jklol.ccg.lexicon.UnknownWordLexicon;
import com.jayantkrish.jklol.ccg.supertag.SupertagAnnotation;
import com.jayantkrish.jklol.ccg.supertag.SupertagLexiconScorer;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteFactor.Outcome;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.training.NullLogFunction;
import com.jayantkrish.jklol.util.Assignment;

public class CcgParserTest extends TestCase {

  CcgParser parser, parserWithComposition, parserWithCompositionNormalForm,
  parserWithUnary,parserWithUnaryAndComposition, parserWordSkip, parserWithString;

  ExpressionParser<Expression2> exp;
  ExpressionSimplifier simplifier;

  private static final String[] lexicon = { "i,N{0},i,0 i",
      "people,N{0},people,0 people",
      "berries,N{0},berries,0 berries",
      "apple,N{0},apple,0 apple",
      "houses,N{0},houses,0 houses",
      "eat,((S[b]{0}\\N{1}){0}/N{2}){0},(lambda ($2 $1) (exists a b (and ($1 a) ($2 b) (eat a b)))),0 eat,eat 1 1,eat 2 2",
      "that,((N{1}\\N{1}){0}/(S[0]{2}\\N{1}){2}){0},(lambda ($2 $1) (lambda (x) (and ($1 x) ($2 (lambda (y) (equals y x)))))),0 that,that 1 1,that 2 2",
      "that,((N{1}\\N{1}){0}/(S[0]{2}/N{3}){2}){0},(lambda ($2 $1) (lambda (x) (and ($1 x) ($2 (lambda (y) (equals y x)))))),0 that,that 1 1,that 2 2",
      "quickly,(((S[1]{1}\\N{2}){1}/N{3}){1}/((S[1]{1}\\N{2}){1}/N{3}){1}){0},(lambda ($1) $1),0 quickly,quickly 1 1",
      "in,((N{1}\\N{1}){0}/N{2}){0},(lambda ($2 $1) (lambda (c) (exists d (and ($1 c) ($2 d) (in c d))))),0 in,in 1 1,in 2 2",
      "amazingly,((N{1}/N{1}){2}/(N{1}/N{1}){2}){0},,0 amazingly,amazingly 1 2",
      "tasty,(N{1}/N{1}){0},(lambda ($1) (lambda (e) (and (tasty e) ($1 e)))),0 tasty,tasty 1 1",
      "in,(((S[1]{1}\\N{2}){1}\\(S[1]{1}\\N{2}){1}){0}/N{3}){0},,0 in,in 1 1,in 2 3",
      "and,((N{1}\\N{1}){0}/N{1}){0},,0 and",
      "almost,(((N{1}\\N{1}){2}/N{3}){2}/((N{1}\\N{1}){2}/N{3}){2}){0},,0 almost,almost 1 2",
      "is,((S[b]{0}\\N{1}){0}/N{2}){0},,0 is,is 1 1, is 2 2",
      "directed,((S[b]{0}\\N{1}){0}/N{2}){0},,0 directed,directed 1 2,directed 2 1",
      ";,;{0},;,0 ;",
      "or,conj{0},word:or,0 or",
      "about,(NP{0}/(S[1]{1}\\N{2}){1}){0},,0 about,about 1 1",
      "eating,((S[ng]{0}\\N{1}){0}/N{2}){0},,0 eat,eat 1 1,eat 2 2",
      "rapidly,((S[1]{1}\\N{2}){1}/(S[1]{1}\\N{2}){1}){0},,0 rapidly,rapidly 1 1",
      "colorful,(N{1}/N{1}){0},(lambda ($1) (lambda (e) (and (colorful e) ($1 e)))),0 colorful,colorful 1 1",
      "*not_a_word*,(NP{0}/N{1}){0},,0 *not_a_word*",
      "near,((S[1]{1}/(S[1]{1}\\N{0}){1}){0}/N{2}){0},,0 near,near 2 2",
      "the,(N{0}/N{0}){1},(lambda ($1) $1),1 the,the 1 0",
      "exactly,(S[1]{1}/S[1]{1}){0},,0 exactly,exactly 1 1",
      "green,(N{0}/N{0}){1},,1 green,green_(N{0}/N{0}){1} 1 0",
      "blue,N{0},blue,0 blue",
      "blue,(N{0}/N{0}){1},blue,0 blue",
      "backward,(N{1}\\N{1}){0},backward,0 backward,backward 1 1",
      "a,(NP{1}/N{1}){0},,0 a,a 1 1",
      "#,#{0},#,0 #",
      "stringfunc,(S{0}/String{1}){0},(lambda ($1) (stringFunc $1)),0 stringfunc,stringfunc 1 1",
      };
  
  private static final String[] unknownLexicon = {
    "NN,N{0},,0 unk-nn",
  };

  private static final String DEFAULT_POS = ParametricCcgParser.DEFAULT_POS_TAG;

  private static final double[] weights = { 0.5, 1.0, 1.0, 1.0, 1.0,
      0.3, 1.0, 1.0,
      1.0, 1.0,
      1.0, 1.0,
      0.5, 1.0, 2.0,
      0.25, 1.0,
      1.0, 0.5,
      1.0, 1.0,
      0.5, 1.0,
      1.0, 1.0, 1.0, 1.0, 0.75, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0};

  private static final double[] unknownWeights = { 3.0 };

  private static final String[] binaryRuleArray = { ";{1} N{0} N{0}", "N{0} ;{1} N{0},(lambda ($L $R) $L)",
      ";{2} (S[0]{0}\\N{1}){0} (N{0}\\N{1}){0}", "\",{2} N{0} (N{0}\\N{0}){1}\"",
      "conj{1} N{0} (N{0}\\N{0}){1},(lambda ($L $R) (lambda ($0) (lambda (x) (forall (pred (set $R $0)) (pred x))))),CONJUNCTION",
      "conj{2} (S[0]{0}\\N{1}){0} ((S[0]{0}\\N{1}){0}\\(S[0]{0}\\N{1}){0}){2},,CONJUNCTION",
      "\"N{0} N{1} N{1}\",\"(lambda ($L $R) (lambda (j) (exists k (and ($L k) ($R j) (special:compound k j)))))\",\"OTHER\",\"special:compound 1 0\",\"special:compound 2 1\"",
      "#{5} N{1} (N{0}\\N{0}){1},(lambda ($C $R) (lambda ($L) (lambda (x) (exists y (and ($L x) ($R y) (compound y x)))))),OTHER,special:compound 1 0,special:compound 2 1"
  };

  private static final String[] unaryRuleArray = { "N{0} (S[1]{1}/(S[1]{1}\\N{0}){1}){1}",
      "N{0} (N{1}/N{1}){0}", "((S[0]{0}\\N{1}){0}/N{2}){0} ((S[0]{0}/NP{1}){0}/NP{2}){0}",
      "(S[ng]{0}\\N{1}){0} (N{2}\\N{2}){0}"};

  // Syntactic CCG weights to set. All unlisted combinations get
  // weight 1.0, all listed combinations get weight 1.0 + given weight
  private static final String[][] syntacticCombinations = {
      { "(N{1}\\N{1}){0}/(S[0]{2}\\N{1}){2}){0}", "(S[b]{2}\\N{1}){2}" },
      { "(NP{0}/(S[1]{1}\\N{2}){1}){0}", "((S[ng]{0}\\N{1}){0}/N{2}){0}" },
  };
  private static final double[] syntacticCombinationWeights = {
      2.0, -0.75
  };
  
  private static final String[][] dependencyCombinations = {
    {"eat", "((S[b]{0}\\N{1}){0}/N{2}){0}", "2", "berries", DEFAULT_POS, DEFAULT_POS},
    {"eat", "((S[ng]{0}\\N{1}){0}/N{2}){0}", "2", "berries", DEFAULT_POS, DEFAULT_POS},
    {"quickly", "(((S[1]{1}\\N{2}){1}/N{3}){1}/((S[1]{1}\\N{2}){1}/N{3}){1}){0}", "1", "eat", DEFAULT_POS, DEFAULT_POS},
    {"in", "((N{1}\\N{1}){0}/N{2}){0}", "1", "people", DEFAULT_POS, DEFAULT_POS},
    {"special:compound", "N{0}", "1", "people", DEFAULT_POS, DEFAULT_POS},
    {"green_(N{0}/N{0}){1}", "(N{1}/N{1}){0}", "1", "people", DEFAULT_POS, DEFAULT_POS},
    {"tasty", "(N{1}/N{1}){0}", "1", "berries", "JJ", "NN"},
    {"stringfunc", "(S{0}/N{1}){0}", "1", "special:string", DEFAULT_POS, DEFAULT_POS},
  };

  private static final double[] dependencyWeightIncrements = {
    1.0, 1.0, 3.0, 1.0, 1.0, 1.0, 3.0, 1.0,
  };

  private VariableNumMap terminalVar;
  private VariableNumMap ccgCategoryVar;

  private VariableNumMap posTagVar;
  private VariableNumMap terminalSyntaxVar;

  private VariableNumMap semanticHeadVar, semanticSyntaxVar, semanticArgNumVar,
  semanticArgVar, semanticHeadPosVar, semanticArgPosVar;

  public void setUp() {
    parser = parseLexicon(lexicon, unknownLexicon, binaryRuleArray, new String[] { "FOO{0} FOO{0}" }, weights, unknownWeights, false, false, false, false);
    parserWithComposition = parseLexicon(lexicon, unknownLexicon, binaryRuleArray, new String[] { "FOO{0} FOO{0}" }, weights, unknownWeights, true, false, false, false);
    parserWithCompositionNormalForm = parseLexicon(lexicon, unknownLexicon, binaryRuleArray, new String[] { "FOO{0} FOO{0}" }, weights, unknownWeights, true, false, true, false);
    parserWithUnary = parseLexicon(lexicon, unknownLexicon, binaryRuleArray, unaryRuleArray, weights, unknownWeights, false, false, false, false);
    parserWithUnaryAndComposition = parseLexicon(lexicon, unknownLexicon, binaryRuleArray,
        new String[] { "N{0} (S[1]{1}/(S[1]{1}\\N{0}){1}){1},(lambda ($0) (lambda ($1) ($1 $0)))" }, weights, unknownWeights, true, false, false, false);

    parserWordSkip = parseLexicon(lexicon, unknownLexicon, binaryRuleArray, new String[] { "FOO{0} FOO{0}" }, weights, unknownWeights, false, true, false, false);
    
    parserWithString = parseLexicon(lexicon, unknownLexicon, binaryRuleArray, new String[] { "String{0} N{0},(lambda (x) x)" }, weights, unknownWeights, false, true, false, true);

    exp = ExpressionParser.expression2();
    simplifier = new ExpressionSimplifier(Arrays.
        <ExpressionReplacementRule>asList(new LambdaApplicationReplacementRule(),
            new VariableCanonicalizationReplacementRule()));
  }

  public void testLexiconPosBackoff() {
    // test that backoff occurs for out-of-lexicon words.
    List<CcgParse> parses = beamSearch(parser, Arrays.asList("NOT_IN_LEXICON"), Arrays.asList("NN"), 10);
    assertEquals(1, parses.size());
    CcgParse parse = parses.get(0);
    assertEquals(6.0, parse.getSubtreeProbability());
    assertEquals("N", parse.getSyntacticCategory().getValue());
    assertEquals("NN", parse.getLexiconEntry().getLexiconTrigger());
    assertEquals("NN", parse.getSpannedLexiconEntries().get(0).getLexiconTrigger());

    // No backoff should happen if the word is in the lexicon.
    parses = beamSearch(parser, Arrays.asList("a"), Arrays.asList("NN"), 10);
    assertEquals(1, parses.size());
    parse = parses.get(0);
    assertEquals(1.0, parse.getSubtreeProbability());
    assertEquals(SyntacticCategory.parseFrom("NP/N"), parse.getSyntacticCategory());
    assertEquals(Arrays.asList("a"), parse.getLexiconEntry().getLexiconTrigger());
    assertEquals(Arrays.asList("a"), parse.getSpannedLexiconEntries().get(0).getLexiconTrigger());

    // Capitalization doesn't affect whether the word is in the lexicon or not.
    parses = beamSearch(parser, Arrays.asList("A"), Arrays.asList("NN"), 10);
    assertEquals(1, parses.size());
    parse = parses.get(0);
    assertEquals(1.0, parse.getSubtreeProbability());
    assertEquals(SyntacticCategory.parseFrom("NP/N"), parse.getSyntacticCategory());
    assertEquals(Arrays.asList("a"), parse.getLexiconEntry().getLexiconTrigger());
    assertEquals(Arrays.asList("a"), parse.getSpannedLexiconEntries().get(0).getLexiconTrigger());
  }

  public void testSyntacticCategoryBackoff() {
    // Test that word / syntactic category weights work.
    List<CcgParse> parses = beamSearch(parser, Arrays.asList("blue"), 10);
    assertEquals(2, parses.size());
    CcgParse best = parses.get(0);
    CcgParse second = parses.get(1);
    System.out.println(best.getSubtreeProbability());
    System.out.println(second.getSubtreeProbability());
    
    assertEquals(3.0, best.getSubtreeProbability());
    assertEquals(SyntacticCategory.parseFrom("N"), best.getSyntacticCategory());
    
    
    assertEquals(1.0, second.getSubtreeProbability());
    assertEquals(SyntacticCategory.parseFrom("N/N"), second.getSyntacticCategory());
  }

  public void testBeamSearch() {
    List<CcgParse> parses = beamSearch(parser, Arrays.asList("I", "quickly", "eat", "amazingly", "tasty", "berries"), 20);
    assertEquals(1, parses.size());
    CcgParse parse = parses.get(0);
    // CcgParse parse = parse(parser, Arrays.asList("I", "quickly", "eat", "amazingly", "tasty", "berries"));

    System.out.println(parse);
    System.out.println(parse.getAllDependencies());

    assertEquals(2.0, parse.getNodeProbability());
    assertEquals(4.0, parse.getRight().getNodeProbability());
    System.out.println(parse.getRight().getLeft());
    assertEquals(4.0, parse.getRight().getLeft().getNodeProbability());
    assertEquals(1.5, parse.getLeft().getNodeProbability());
    assertEquals(1.5 * 0.3 * 4.0 * 4.0 * 2.0, parse.getSubtreeProbability());

    assertEquals(5, parse.getAllDependencies().size());

    assertEquals("eat", parse.getNodeDependencies().get(0).getHead());
    assertEquals(1, parse.getNodeDependencies().get(0).getArgIndex());
    assertEquals("i", parse.getNodeDependencies().get(0).getObject());
    assertEquals("quickly", parse.getRight().getLeft().getAllDependencies().get(0).getHead());
    assertEquals(1, parse.getRight().getLeft().getAllDependencies().get(0).getArgIndex());
    assertEquals("eat", parse.getRight().getLeft().getAllDependencies().get(0).getObject());

    assertEquals("eat", Iterables.getOnlyElement(parse.getSemanticHeads()).getHead());
    assertEquals("i", Iterables.getOnlyElement(parse.getLeft().getSemanticHeads()).getHead());

    List<DependencyStructure> eatDeps = parse.getDependenciesWithHeadWord(2);
    assertEquals(2, eatDeps.size());
  }
  
  public void testExactParse() {
    CcgParse parse = parse(parser, Arrays.asList("I", "quickly", "eat", "amazingly", "tasty", "berries"));

    System.out.println(parse.getAllDependencies());

    assertEquals(2.0, parse.getNodeProbability());
    assertEquals(4.0, parse.getRight().getNodeProbability());
    System.out.println(parse.getRight().getLeft());
    assertEquals(4.0, parse.getRight().getLeft().getNodeProbability());
    assertEquals(1.5, parse.getLeft().getNodeProbability());
    assertEquals(1.5 * 0.3 * 4.0 * 4.0 * 2.0, parse.getSubtreeProbability());

    assertEquals(5, parse.getAllDependencies().size());

    assertEquals("eat", parse.getNodeDependencies().get(0).getHead());
    assertEquals(1, parse.getNodeDependencies().get(0).getArgIndex());
    assertEquals("i", parse.getNodeDependencies().get(0).getObject());
    assertEquals("quickly", parse.getRight().getLeft().getAllDependencies().get(0).getHead());
    assertEquals(1, parse.getRight().getLeft().getAllDependencies().get(0).getArgIndex());
    assertEquals("eat", parse.getRight().getLeft().getAllDependencies().get(0).getObject());

    assertEquals("eat", Iterables.getOnlyElement(parse.getSemanticHeads()).getHead());
    assertEquals("i", Iterables.getOnlyElement(parse.getLeft().getSemanticHeads()).getHead());

    List<DependencyStructure> eatDeps = parse.getDependenciesWithHeadWord(2);
    assertEquals(2, eatDeps.size());
  }

  public void testBeamSearch2() {
    List<CcgParse> parses = beamSearch(parser, 
        Arrays.asList("people", "that", "quickly", "eat", "berries", "in", "houses"), 100);

    for (CcgParse parse : parses) {
      System.out.println(parse);
      System.out.println(parse.getAllDependencies());
      System.out.println(parse.getSubtreeProbability());
    }

    assertEquals(3, parses.size());

    System.out.println(parses.get(0).getSubtreeProbability() + " " + parses.get(0));
    System.out.println(parses.get(0).getAllDependencies());
    System.out.println(parses.get(1).getSubtreeProbability() + " " + parses.get(1));
    System.out.println(parses.get(1).getAllDependencies());
    System.out.println(parses.get(2).getSubtreeProbability() + " " + parses.get(2));
    System.out.println(parses.get(2).getAllDependencies());

    // The parse where "in" attaches to "people" should have higher
    // probability.
    CcgParse parse = parses.get(0);
    assertEquals("in", parse.getNodeDependencies().get(0).getHead());
    assertEquals(1, parse.getNodeDependencies().get(0).getArgIndex());
    assertEquals("people", parse.getNodeDependencies().get(0).getObject());
    assertEquals(0.3 * 4 * 2 * 2 * 3 * 4, parse.getSubtreeProbability());
    assertEquals("people", Iterables.getOnlyElement(parse.getSemanticHeads()).getHead());

    parse = parses.get(1);
    assertEquals(2, parse.getNodeDependencies().size());
    // Parse should have "people" as an arg1 dependency for both
    // "that" and "eat"
    assertEquals(1, parse.getNodeDependencies().get(0).getArgIndex());
    assertEquals("people", parse.getNodeDependencies().get(0).getObject());
    assertEquals(1, parse.getNodeDependencies().get(1).getArgIndex());
    assertEquals("people", parse.getNodeDependencies().get(1).getObject());
    assertEquals("people", Iterables.getOnlyElement(parse.getSemanticHeads()).getHead());

    Set<String> heads = Sets.newHashSet(parse.getNodeDependencies().get(0).getHead(),
        parse.getNodeDependencies().get(1).getHead());
    assertTrue(heads.contains("that"));
    assertTrue(heads.contains("eat"));

    assertEquals(0.3 * 4 * 2 * 3 * 4, parse.getSubtreeProbability());
  }
  
  public void testExactParse2() {
    CcgParse parse = parse(parser, 
        Arrays.asList("people", "that", "quickly", "eat", "berries", "in", "houses"));

    // The parse where "in" attaches to "people" should have higher
    // probability.
    assertEquals("in", parse.getNodeDependencies().get(0).getHead());
    assertEquals(1, parse.getNodeDependencies().get(0).getArgIndex());
    assertEquals("people", parse.getNodeDependencies().get(0).getObject());
    assertEquals(0.3 * 4 * 2 * 2 * 3 * 4, parse.getSubtreeProbability());
    assertEquals("people", Iterables.getOnlyElement(parse.getSemanticHeads()).getHead());
  }

  public void testBeamSearch3() {
    List<CcgParse> parses = beamSearch(parser, Arrays.asList("green", "people"), 10);

    assertEquals(1, parses.size());
    assertEquals(2.0 * 0.75, parses.get(0).getSubtreeProbability());
  }
  
  public void testExactParse3() {
    CcgParse parse = parse(parser, Arrays.asList("green", "people"));
    assertEquals(2.0 * 0.75, parse.getSubtreeProbability());
  }

  public void testParseLogicalFormApplication() {
    runLogicalFormTest(parser, Arrays.asList("i", "quickly", "eat", "berries"),
        "(exists a b (and (i a) (berries b) (eat a b)))");
  }

  public void testParseLogicalFormApplication2() {
    runLogicalFormTest(parser, Arrays.asList("i", "that", "eat", "berries"),
        "(lambda (x) (and (i x) (exists a b (and (equals a x) (berries b) (eat a b)))))");
  }

  public void testParseLogicalFormComposition() {
    runLogicalFormTest(parserWithComposition, Arrays.asList("the", "colorful", "tasty"), 
        "(lambda ($1) (lambda (e) (and (colorful e) (and (tasty e) ($1 e)))))");
  }

  public void testParseLogicalFormBinaryRule() {
    List<CcgParse> parses = beamSearch(parserWithComposition, Arrays.asList(
        ";", "berries"), 10);
    assertEquals(1, parses.size());
    assertEquals(null, parses.get(0).getLogicalForm());
  }

  public void testParseLogicalFormBinaryRule2() {
    runLogicalFormTest(parserWithComposition, Arrays.asList("berries", ";"), "berries");
  }

  public void testParseLogicalFormBinaryRule3() {
    runLogicalFormTest(parserWithComposition, Arrays.asList("people", "berries"),
        "(lambda (j) (exists k (and (people k) (berries j) (special:compound k j))))");
  }

  public void testParseLogicalFormConjunction() {
    runLogicalFormTest(parserWithComposition, Arrays.asList("i", "eat", "people", "or", "berries"), 
        "(exists a b (and (i a) (forall (pred (set berries people)) (pred b)) (eat a b)))");
  }

  public void testParseLogicalFormUnary() {
    List<CcgParse> parses = beamSearch(parserWithUnaryAndComposition,
        Arrays.asList("berries", "that", "i", "eat"), 10);

    Expression2 expectedLf = exp.parse(
        "(lambda (x) (and (berries x) (exists a b (and (i a) (equals b x) (eat a b)))))");
    boolean foundN = false;
    for (CcgParse parse : parses) {
      String root = parse.getSyntacticCategory().getValue();
      if (root != null && root.equals("N")) {
        assertEquals(simplifier.apply(expectedLf), simplifier.apply(parse.getLogicalForm()));
        foundN = true;
      }
    }
    assertTrue(foundN);
  }

  private List<CcgParse> runLogicalFormTest(CcgParser parser, List<String> words,
      String expectedExpression) {
    List<CcgParse> parses = beamSearch(parser, words, 10);
    // assertEquals(1, parses.size());
    assertTrue(parses.size() > 0);
    Expression2 expectedLf = exp.parse(expectedExpression);
    for (CcgParse parse : parses) {
      assertEquals(simplifier.apply(expectedLf), simplifier.apply(parse.getLogicalForm()));
    }
    return parses;
  }

  public void testParseComposition() {
    assertEquals(0, beamSearch(parser, Arrays.asList("rapidly", "eat"), 10).size());

    List<CcgParse> parses = beamSearch(parserWithComposition, Arrays.asList("rapidly", "eat"), 10);

    assertEquals(1, parses.size());
    CcgParse parse = parses.get(0);
    System.out.println(parse);
    System.out.println(parse.getAllDependencies());

    Set<DependencyStructure> deps = Sets.newHashSet(parse.getAllDependencies());
    Set<DependencyStructure> expectedDeps = Sets.newHashSet(
        parseDependency("rapidly", "((S[1]{1}\\N{2}){1}/(S[1]{1}\\N{2}){1}){0}", 0, "eat", 1, 1));
    assertEquals(expectedDeps, deps);

    SyntacticCategory expectedSyntax = SyntacticCategory.parseFrom("(S[b]\\N)/N");
    assertEquals(expectedSyntax, parse.getSyntacticCategory());
  }

  public void testParseComposition2() {
    assertEquals(0, beamSearch(parser, Arrays.asList("eat", "amazingly", "tasty"), 10).size());

    List<CcgParse> parses = beamSearch(parserWithComposition, Arrays.asList("eat", "amazingly", "tasty"), 10);

    assertEquals(1, parses.size());
    CcgParse parse = parses.get(0);

    Set<DependencyStructure> deps = Sets.newHashSet(parse.getAllDependencies());
    Set<DependencyStructure> expectedDeps = Sets.newHashSet(
        parseDependency("amazingly", "((N{1}/N{1}){2}/(N{1}/N{1}){2}){0}", 1, "tasty", 2, 1));
    assertEquals(expectedDeps, deps);

    SyntacticCategory expectedSyntax = SyntacticCategory.parseFrom("(S[b]\\N)/N");
    assertEquals(expectedSyntax, parse.getSyntacticCategory());
    System.out.println(parse.getHeadedSyntacticCategory());

    Set<IndexedPredicate> heads = parse.getSemanticHeads();
    assertEquals(1, heads.size());
    assertEquals("eat", Iterables.getOnlyElement(heads).getHead());
  }

  public void testParseComposition3() {
    assertEquals(1, beamSearch(parser, Arrays.asList("about", "eating", "berries"), 10).size());

    List<CcgParse> parses = beamSearch(parserWithComposition, Arrays.asList("about", "eating", "berries"), 10);

    for (CcgParse parse : parses) {
      System.out.println(parse);
      System.out.println(parse.getAllDependencies());
    }

    assertEquals(2, parses.size());
    for (CcgParse parse : parses) {
      Set<DependencyStructure> deps = Sets.newHashSet(parse.getAllDependencies());
      Set<DependencyStructure> expectedDeps = Sets.newHashSet(
          parseDependency("about", "(NP{0}/(S[1]{1}\\N{2}){1}){0}", 0, "eat", 1, 1),
          parseDependency("eat", "((S[ng]{0}\\N{1}){0}/N{2}){0}", 1, "berries", 2, 2));
      assertEquals(expectedDeps, deps);

      HeadedSyntacticCategory expectedSyntax = HeadedSyntacticCategory.parseFrom("NP{0}");
      assertEquals(expectedSyntax, parse.getHeadedSyntacticCategory());

      Set<IndexedPredicate> heads = parse.getSemanticHeads();
      assertEquals(1, heads.size());
      assertEquals("about", Iterables.getOnlyElement(heads).getHead());
    }

    assertEquals(2.0 * 1, parses.get(0).getSubtreeProbability());
    assertEquals(0.5 * 1, parses.get(1).getSubtreeProbability());
  }

  public void testParseComposition4() {
    List<CcgParse> parses = beamSearch(parserWithComposition, 
        Arrays.asList("i", "quickly", "eat", "amazingly", "tasty", "berries"), 20);
    assertEquals(3, parses.size());

    Set<DependencyStructure> expectedDeps = Sets.newHashSet(
        parseDependency("eat", "((S[b]{0}\\N{1}){0}/N{2}){0}", 2, "i", 0, 1),
        parseDependency("eat", "((S[b]{0}\\N{1}){0}/N{2}){0}", 2, "berries", 5, 2),
        parseDependency("quickly", "(((S[1]{1}\\N{2}){1}/N{3}){1}/((S[1]{1}\\N{2}){1}/N{3}){1}){0}", 1, "eat", 2, 1),
        parseDependency("amazingly", "((N{1}/N{1}){2}/(N{1}/N{1}){2}){0}", 3, "tasty", 4, 1),
        parseDependency("tasty", "(N{1}/N{1}){0}", 4, "berries", 5, 1));

    for (CcgParse parse : parses) {
      assertEquals(expectedDeps, Sets.newHashSet(parse.getAllDependencies()));
    }
  }

  public void testParseComposition5() {
    List<CcgParse> parses = beamSearch(parserWithComposition, 
        Arrays.asList("exactly", "eat"), 10);
    assertEquals(1, parses.size());

    assertEquals(HeadedSyntacticCategory.parseFrom("((S[b]{0}\\N{1}){0}/N{2}){0}"),
        parses.get(0).getHeadedSyntacticCategory());
  }
  
  public void testParseCompositionNormalForm() {
    List<CcgParse> parses = beamSearch(parserWithComposition, Arrays.asList("green", "green", "berries"), 10);
    // The parser with composition permits 2 derivations, one using
    // composition of the greens, and one only using application.
    assertEquals(2, parses.size());

    // The normal form parser only permits the application-only derivation
    parses = beamSearch(parserWithCompositionNormalForm, Arrays.asList("green", "green", "berries"), 10);
    assertEquals(1, parses.size());
    CcgParse parse = parses.get(0);

    assertTrue(parse.getLeft().isTerminal());
    assertFalse(parse.getRight().isTerminal());
    assertEquals("N", parse.getRight().getSyntacticCategory().getValue());

    // The same property should generalize to much longer sentences.
    parses = beamSearch(parserWithCompositionNormalForm, Arrays.asList("green", "green", "green", "green",
        "green", "green", "green", "green", "green", "berries"), 1000);
    assertEquals(1, parses.size());
  }

  public void testParseWordSkip() {
    List<String> words = Arrays.asList("green", "green", "i");
    List<CcgParse> parses = beamSearch(parserWordSkip, words, 10);

    double[] probs = new double[parses.size()];
    for (int i = 0; i < parses.size(); i++) {
      CcgParse parse = parses.get(i);
      probs[i] = parse.getSubtreeProbability();
    }

    assertEquals(6, parses.size());
    assertTrue(Ordering.natural().reverse().isOrdered(Doubles.asList(probs)));

    words = Arrays.asList("green", "green", "green", "i");
    parses = beamSearch(parserWordSkip, words, 30);
    for (CcgParse parse : parses) {
      System.out.println(parse);
    }

    assertEquals(11, parses.size());
  }

  public void testParseWordSkipExact() {
    List<String> words = Arrays.asList("green", "green", "i");
    CcgParse bestParse = parse(parserWordSkip, words);

    System.out.println(bestParse);
    System.out.println(bestParse.getLogicalForm());
    System.out.println(bestParse.getSubtreeProbability());
    
    assertEquals(0, bestParse.getSpanStart());
    assertEquals(2, bestParse.getSpanEnd());
    // This fails because the features aren't applied to the same spans
    // anymore with word skipping.
    assertEquals(Arrays.asList("i"), ((SkipTrigger) bestParse.getLexiconEntry().getLexiconTrigger()).getTrigger());
    assertEquals(1.5, bestParse.getSubtreeProbability());

    // TODO: test that dependencies are projected from the spans of the original
    // lexicon entries.
  }

  public void testParseHeadedSyntaxWeights() {
    List<CcgParse> parses = beamSearch(parser, Arrays.asList("tasty", "apple"), 10);
    assertEquals(1, parses.size());
    assertEquals(4.0, parses.get(0).getSubtreeProbability(), 0.0001);
    
    parses = beamSearch(parser, Arrays.asList("tasty", "apple", "or", "berries"), 
        Arrays.asList(DEFAULT_POS, DEFAULT_POS, DEFAULT_POS, "JJ"), 10);
    assertEquals(2, parses.size());
    
    CcgParse parse = parses.get(0);
    assertEquals(HeadedSyntacticCategory.parseFrom("(N{0}/N{0}){1}").getCanonicalForm(),
        parse.getLeft().getHeadedSyntacticCategory());
    assertEquals(6.0, parse.getSubtreeProbability(), 0.00001);
  }

  public void testParseHeadUnification() {
    List<CcgParse> parses = beamSearch(parser, 
        Arrays.asList("people", "and", "houses", "eat", "berries", "and", "berries"), 10);

    assertEquals(1, parses.size());

    // Both parses should have the same probability and dependencies.
    CcgParse parse = parses.get(0);
    System.out.println(parse.getAllDependencies());
    assertEquals(0.3 * 2 * 2 * 2 * 4 * 2, parse.getSubtreeProbability());
    assertEquals(2, parse.getNodeDependencies().size());
    assertEquals("eat", parse.getNodeDependencies().get(0).getHead());
    String object = parse.getNodeDependencies().get(0).getObject();
    assertTrue(object.equals("people") || object.equals("houses"));
    assertEquals(2, parse.getRight().getNodeDependencies().size());
    assertEquals("berries", parse.getRight().getNodeDependencies().get(0).getObject());

    assertEquals("eat", Iterables.getOnlyElement(parse.getSemanticHeads()).getHead());

    Set<String> heads = Sets.newHashSet();
    for (IndexedPredicate predicate : parse.getLeft().getSemanticHeads()) {
      heads.add(predicate.getHead());
    }
    assertEquals(Sets.newHashSet("people", "houses"), heads);
  }

  public void testPrepositionalModifier() {
    List<CcgParse> parses = beamSearch(parser, 
        Arrays.asList("people", "almost", "in", "houses"), 10);

    assertEquals(1, parses.size());

    CcgParse parse = parses.get(0);
    System.out.println(parse.getAllDependencies());
  }

  public void testSubjectPatterns() {
    List<CcgParse> parses = beamSearch(parser, 
        Arrays.asList("people", "that", "directed", "berries"), 10);

    assertEquals(1, parses.size());

    CcgParse parse = parses.get(0);
    System.out.println(parse.getAllDependencies());

    // Make sure that the 2nd argument dependency of directed
    // gets mapped to people via the category of "that".
    assertEquals(2, parse.getNodeDependencies().size());
    for (DependencyStructure dep : parse.getNodeDependencies()) {
      if (dep.getHead().equals("directed")) {
        assertEquals(2, dep.getArgIndex());
        assertEquals("people", dep.getObject());
      } else if (dep.getHead().equals("that")) {
        assertEquals(1, dep.getArgIndex());
        assertEquals("people", dep.getObject());
      }
    }
  }

  public void testLexiconPosParameters() {
    List<CcgParse> nnParses = beamSearch(parser, 
        Arrays.asList("tasty", "berries"), 
        Arrays.asList(DEFAULT_POS, "NN"), 10);
    
    List<CcgParse> basicParses = beamSearch(parser, 
        Arrays.asList("tasty", "berries"), 
        Arrays.asList(DEFAULT_POS, DEFAULT_POS), 10);
    
    assertEquals(1, nnParses.size());
    assertEquals(1, basicParses.size());
    assertEquals(2 * basicParses.get(0).getSubtreeProbability(), nnParses.get(0).getSubtreeProbability());
  }
  
  public void testDependencyPosParameters() {
    List<CcgParse> nnParses = beamSearch(parser, 
        Arrays.asList("tasty", "berries"), 
        Arrays.asList("JJ", "NN"), 10);
    
    List<CcgParse> basicParses = beamSearch(parser, 
        Arrays.asList("tasty", "berries"), 
        Arrays.asList(DEFAULT_POS, "NN"), 10);
    
    assertEquals(1, nnParses.size());
    assertEquals(1, basicParses.size());
    // The tasty JJ dependency is 4x as likely as the DEFAULT_POS one,
    // and the word distance weights contribute a factor of 2. 
    assertEquals(4 * 2 * basicParses.get(0).getSubtreeProbability(), nnParses.get(0).getSubtreeProbability());
  }

  public void testParseUnfilledDep() {
    List<CcgParse> parses = beamSearch(parser, 
        Arrays.asList("about", "eating", "berries"), 10);

    assertEquals(1, parses.size());

    CcgParse parse = parses.get(0);
    Set<DependencyStructure> expectedDeps = Sets.newHashSet(
        parseDependency("about", "(NP{0}/(S[1]{1}\\N{2}){1}){0}", 0, "eat", 1, 1),
        parseDependency("eat", "((S[ng]{0}\\N{1}){0}/N{2}){0}", 1, "berries", 2, 2));
    assertEquals(expectedDeps, Sets.newHashSet(parse.getAllDependencies()));
  }

  public void testBinaryRules1() {
    List<CcgParse> parses = beamSearch(parser, 
        Arrays.asList("berries", ";"), 10);

    assertEquals(1, parses.size());
    CcgParse parse = parses.get(0);
    System.out.println(parse.getAllDependencies());
    System.out.println(parse.getSemanticHeads());

    Set<IndexedPredicate> expectedHeads = Sets.newHashSet(new IndexedPredicate("berries", 0));
    assertEquals(expectedHeads, parse.getSemanticHeads());
  }

  public void testBinaryRules2() {
    List<CcgParse> parses = beamSearch(parser, 
        Arrays.asList("people", "eat", "berries", ";"), 10);

    assertEquals(1, parses.size());
    System.out.println(parses.get(0).getAllDependencies());

    CcgParse parse = parses.get(0);
    assertEquals(0.3 * 2 * 2 * 4, parse.getSubtreeProbability());
    assertEquals(2, parse.getAllDependencies().size());
  }

  public void testBinaryRules3() {
    List<CcgParse> parses = beamSearch(parser, 
        Arrays.asList("people", ";", "eat", "berries", ";"), 10);

    assertEquals(3, parses.size());

    Set<SyntacticCategory> syntaxTypes = Sets.newHashSet();
    for (CcgParse parse : parses) {
      double expectedProb = 0.3 * 2 * 4;
      if (parse.getSyntacticCategory().getValue().equals("S")) {
        expectedProb *= 2;
      }
      assertEquals(expectedProb, parse.getSubtreeProbability());
      assertEquals(2, parse.getAllDependencies().size());
      syntaxTypes.add(parse.getSyntacticCategory());
    }
    Set<SyntacticCategory> expectedTypes = Sets.newHashSet(SyntacticCategory.parseFrom("N"),
        SyntacticCategory.parseFrom("S[b]"));
    assertEquals(expectedTypes, syntaxTypes);
  }

  public void testBinaryRulesConj() {
    List<CcgParse> parses = beamSearch(parser, 
        Arrays.asList("people", "or", "berries"), 10);

    assertEquals(1, parses.size());
    CcgParse parse = parses.get(0);

    Set<String> heads = Sets.newHashSet();
    for (IndexedPredicate pred : parse.getSemanticHeads()) {
      heads.add(pred.getHead());
    }
    Set<String> expectedHeads = Sets.newHashSet("people", "berries");
    assertEquals(expectedHeads, heads);
  }

  public void testBinaryRulesConj2() {
    List<CcgParse> parses = beamSearch(parser, 
        Arrays.asList("or", "directed", "houses"), 10);
    assertEquals(1, parses.size());
    CcgParse parse = parses.get(0);
    assertEquals(SyntacticCategory.parseFrom("((S[b]\\N)\\(S[b]\\N))"),
        parse.getSyntacticCategory());
  }

  public void testBinaryRulesConj3() {
    List<CcgParse> parses = beamSearch(parser, 
        Arrays.asList("people", "eat", "berries", "or", "directed", "houses"), 10);

    assertEquals(1, parses.size());
    CcgParse parse = parses.get(0);
    System.out.println(parse);

    Set<String> heads = Sets.newHashSet();
    for (IndexedPredicate pred : parse.getSemanticHeads()) {
      heads.add(pred.getHead());
    }
    Set<String> expectedHeads = Sets.newHashSet("eat", "directed");
    assertEquals(expectedHeads, heads);

    Set<DependencyStructure> deps = Sets.newHashSet(parse.getAllDependencies());
    Set<DependencyStructure> expectedDeps = Sets.newHashSet(
        parseDependency("eat", "((S[b]{0}\\N{1}){0}/N{2}){0}", 1, "people", 0, 1),
        parseDependency("eat", "((S[b]{0}\\N{1}){0}/N{2}){0}", 1, "berries", 2, 2),
        parseDependency("directed", "((S[b]{0}\\N{1}){0}/N{2}){0}", 4, "people", 0, 2),
        parseDependency("directed", "((S[b]{0}\\N{1}){0}/N{2}){0}", 4, "houses", 5, 1));
    assertEquals(expectedDeps, deps);

    assertEquals(SyntacticCategory.parseFrom("S[b]"), parse.getSyntacticCategory());
  }

  public void testBinaryRulesNounCompound() {
    List<CcgParse> parses = beamSearch(parser, 
        Arrays.asList("people", "berries"), 10);

    assertEquals(1, parses.size());
    CcgParse parse = parses.get(0);
    System.out.println(parse.getAllDependencies());
    assertEquals(2.0, parse.getSubtreeProbability());

    Set<DependencyStructure> observedDeps = Sets.newHashSet(parse.getAllDependencies());
    Set<DependencyStructure> expectedDeps = Sets.newHashSet(
        parseDependency("special:compound", "N{0}", 1, "berries", 1, 2),
        parseDependency("special:compound", "N{0}", 1, "people", 0, 1));
    assertEquals(expectedDeps, observedDeps);

    assertEquals("berries", Iterables.getOnlyElement(parse.getSemanticHeads()).getHead());
  }

  public void testBinaryRulesNounCompoundHeadIndex() {
    List<CcgParse> parses = beamSearch(parser, 
        Arrays.asList("people", "berries", "backward"), 10);
    assertEquals(2, parses.size());

    Multimap<Integer, DependencyStructure> map1 = parses.get(0).getAllDependenciesIndexedByHeadWordIndex();
    Multimap<Integer, DependencyStructure> map2 = parses.get(1).getAllDependenciesIndexedByHeadWordIndex();
    // One parse has the noun compound dependency projected from
    // "berries"
    // and the other has the dependency projected from "backward"
    assertTrue((map1.get(2).size() == 1 && map2.get(2).size() == 3) ||
        map1.get(2).size() == 3 && map2.get(2).size() == 1);
  }

  public void testBinaryRulesNounCompoundHeadIndexConjunction() {
    List<CcgParse> parses = beamSearch(parser, 
        Arrays.asList("people", "berries", "and", "people"), 10);

    assertEquals(2, parses.size());
    CcgParse parse = parses.get(1);
    Set<DependencyStructure> deps = Sets.newHashSet(parse.getAllDependencies());

    Set<DependencyStructure> expected = Sets.newHashSet(
        parseDependency("special:compound", "N{0}", 3, "people", 3, 2),
        parseDependency("special:compound", "N{0}", 3, "berries", 1, 2),
        parseDependency("special:compound", "N{0}", 3, "people", 0, 1));

    assertEquals(expected, deps);
  }
  
  public void testBinaryRuleUnfilledDependencies() {
    List<CcgParse> parses = beamSearch(parser, 
        Arrays.asList("people", "#", "berries"), 10);

    assertEquals(1, parses.size());
    CcgParse parse = parses.get(0);

    Set<DependencyStructure> expected = Sets.newHashSet(
        parseDependency("special:compound", "N{0}", 2, "people", 0, 1),
        parseDependency("special:compound", "N{0}", 2, "berries", 2, 2));

    assertEquals(expected, Sets.newHashSet(parse.getAllDependencies()));
  }

  // This test exists purely to test parsing speed with large conjunctions.
  public void testLargeConjunction() {
    List<CcgParse> parses = beamSearch(parserWithCompositionNormalForm, 
        Arrays.asList("people", "or", "berries", "or", "people", "or", "people", "or", "people",
            "or", "people"), 1000);
    // "eat", "berries", "or", "eat", "people"
    
    System.out.println(parses.size());
    
    for (CcgParse parse : parses) {
      System.out.println(parse.getSubtreeProbability() + " " + parse);
    }
    
    assertEquals(1, parses.size());
  }
  
  public void testLargeConjunction2() {
    List<CcgParse> parses = beamSearch(parserWithCompositionNormalForm, 
        Arrays.asList("people", "in", "berries", "or", "people", "in", "people", "or", "people",
            "in", "people", "or", "people", "in", "berries"), 1000);

    System.out.println(parses.size());

    for (CcgParse parse : parses) {
      System.out.println(parse.getSubtreeProbability() + " " + parse);
    }
  }

  public void testParseUnaryRules1() {
    List<CcgParse> parses = beamSearch(parserWithUnary,
        Arrays.asList("people", "eat", "berries", "or", "directed", "houses"), 10);

    for (CcgParse parse : parses) {
      System.out.println(parse);
    }

    assertEquals(2, parses.size());
    Set<DependencyStructure> expectedDeps = Sets.newHashSet(
        parseDependency("eat", "((S[b]{0}\\N{1}){0}/N{2}){0}", 1, "people", 0, 1),
        parseDependency("eat", "((S[b]{0}\\N{1}){0}/N{2}){0}", 1, "berries", 2, 2),
        parseDependency("directed", "((S[b]{0}\\N{1}){0}/N{2}){0}", 4, "people", 0, 2),
        parseDependency("directed", "((S[b]{0}\\N{1}){0}/N{2}){0}", 4, "houses", 5, 1));

    for (CcgParse parse : parses) {
      assertEquals(expectedDeps, Sets.newHashSet(parse.getAllDependencies()));
    }
  }

  public void testParseUnaryRules2() {
    List<CcgParse> parses = beamSearch(parserWithUnary, 
        Arrays.asList("people", "eat", "people", "berries"), 10);

    assertEquals(4, parses.size());
    Set<DependencyStructure> expectedDeps = Sets.newHashSet(
        parseDependency("eat", "((S[b]{0}\\N{1}){0}/N{2}){0}", 1, "people", 0, 1),
        parseDependency("eat", "((S[b]{0}\\N{1}){0}/N{2}){0}", 1, "berries", 3, 2));

    for (CcgParse parse : parses) {
      Set<DependencyStructure> trueDeps = Sets.newHashSet(parse.getAllDependencies());
      assertTrue(trueDeps.containsAll(expectedDeps));
    }
  }

  public void testParseUnaryRules3() {
    List<CcgParse> parses = beamSearch(parserWithUnary, Arrays.asList("eat"), 10);

    System.out.println(parses);
    assertEquals(2, parses.size());
    Set<SyntacticCategory> expectedCats = Sets.newHashSet(
        SyntacticCategory.parseFrom("(S[b]\\N)/N"), SyntacticCategory.parseFrom("((S[b]/NP)/NP)"));

    Set<SyntacticCategory> actualCats = Sets.newHashSet();
    for (CcgParse parse : parses) {
      actualCats.add(parse.getSyntacticCategory());
    }
    assertEquals(expectedCats, actualCats);
  }
  
  public void testParseUnaryRulesDropArgument() {
    List<CcgParse> parses = beamSearch(parserWithUnary, Arrays.asList("a", "people", "eating", "berries"), 10);
    
    assertEquals(1, parses.size());
    CcgParse parse = parses.get(0);
    assertEquals("NP", parse.getHeadedSyntacticCategory().getSyntax().getValue());
    assertEquals(2, parse.getAllDependencies().size());
    assertEquals(Sets.newHashSet(1), parse.getHeadWordIndexes());
  }

  public void testChartFilterApply() {
    ChartCost filter = new TestChartFilter();
    List<CcgParse> parses = parserWithUnary.beamSearch(new AnnotatedSentence(
        Arrays.asList("I", "eat", "berries", "in", "people", "houses"),
        Collections.nCopies(6, DEFAULT_POS)), 10, filter, new NullLogFunction(), -1, Integer.MAX_VALUE, 1);

    // The filter disallows the verb modifier syntactic category for
    // "in"
    SyntacticCategory expected = SyntacticCategory.parseFrom("(N\\N)/N");
    for (CcgParse parse : parses) {
      assertEquals(expected, parse.getLexiconEntryForWordIndex(3).getCategory().getSyntax().getSyntax());
    }
    
    System.out.println(parses.get(0).toHtmlString());
  }

  public void testSupertagChartFilter() {
    AnnotatedSentence sentence = new AnnotatedSentence(
        Arrays.asList("blue", "berries"), Collections.nCopies(2, DEFAULT_POS));
    List<CcgParse> parses = parser.beamSearch(sentence, 10, new NullLogFunction());
    assertEquals(2, parses.size());

    List<List<HeadedSyntacticCategory>> supertags = Lists.newArrayList();
    List<List<Double>> scores = Lists.newArrayList();
    supertags.add(Lists.newArrayList(HeadedSyntacticCategory.parseFrom("N{0}")));
    scores.add(Lists.newArrayList(1.0));
    supertags.add(Lists.newArrayList(HeadedSyntacticCategory.parseFrom("N{0}")));
    scores.add(Lists.newArrayList(1.0));
    
    SupertagAnnotation annotation = new SupertagAnnotation(supertags, scores);
    AnnotatedSentence annotatedSentence = sentence.addAnnotation("supertags", annotation);

    parses = parser.beamSearch(annotatedSentence, 10, new NullLogFunction());
    assertEquals(1, parses.size());
  }

  public void testSerialization() throws IOException {
    ObjectOutputStream oos = new ObjectOutputStream(new NullOutputStream());
    oos.writeObject(parserWithUnary);
    oos.close();
  }

  public void testStringLexicon() {
    List<CcgParse> parses = beamSearch(parserWithString, Arrays.asList("stringfunc", "bar"), 20);
    assertTrue(parses.size() > 0);
    Expression2 expected = ExpressionParser.expression2().parse("(stringFunc \"bar\")");
    CcgParse theSentence = null;
    for (CcgParse parse : parses) {
      if (parse.getHeadedSyntacticCategory().equals(HeadedSyntacticCategory.parseFrom("S{0}"))) {
        theSentence = parse;
        break;
      }
    }
    assertTrue(theSentence != null);
    Expression2 actual = simplifier.apply(theSentence.getLogicalForm());
    assertEquals(expected, actual);
  }
  
  public void testStringLexiconWholeSentence() {
    List<CcgParse> parses = beamSearch(parserWithString, Arrays.asList("stringfunc", "bar"), 20);
    assertTrue(parses.size() > 0);
    Expression2 expected = ExpressionParser.expression2().parse("unknownCommand");
    CcgParse theSentence = null;
    int numUnknown = 0;
    for (CcgParse parse : parses) {
      if (parse.getHeadedSyntacticCategory().equals(HeadedSyntacticCategory.parseFrom("Unknown{0}"))) {
        theSentence = parse;
        numUnknown++;
      }
    }
    assertTrue(numUnknown == 1);
    Expression2 actual = simplifier.apply(theSentence.getLogicalForm());
    assertEquals(expected, actual);
  }

  public void testStringOneWord() {
    List<CcgParse> parses = beamSearch(parserWithString, Arrays.asList("baz"), 20);
    assertTrue(parses.size() > 0);

    boolean foundN = false;
    boolean foundString = false;
    for (CcgParse parse : parses) {
      System.out.println(parse);
      if (parse.getHeadedSyntacticCategory().equals(HeadedSyntacticCategory.parseFrom("N{0}"))) {
        foundN = true;
      }
      if (parse.getHeadedSyntacticCategory().equals(HeadedSyntacticCategory.parseFrom("String{0}"))) {
        foundString = true;
      }
    }
    assertTrue(foundN);
    assertTrue(foundString);
  }

  public List<CcgParse> beamSearch(CcgParser parser, List<String> words,
      int beamSize) {
    return beamSearch(parser, words, Collections.nCopies(words.size(), DEFAULT_POS), beamSize);
  }

  public List<CcgParse> beamSearch(CcgParser parser, List<String> words,
      List<String> posTags, int beamSize) {
    return parser.beamSearch(new AnnotatedSentence(words, 
        posTags), beamSize, null, new NullLogFunction(), -1, Integer.MAX_VALUE, 16);
  }

  public CcgParse parse(CcgParser parser, List<String> words) {
    return parser.parse(new AnnotatedSentence(words,
        Collections.nCopies(words.size(), DEFAULT_POS)), null, null, -1L, Integer.MAX_VALUE, 16);
  }

  private DependencyStructure parseDependency(String subject, String syntacticCategory, int subjIndex, 
      String object, int objectIndex, int argNum) {
    HeadedSyntacticCategory cat = HeadedSyntacticCategory.parseFrom(syntacticCategory).getCanonicalForm();
    return new DependencyStructure(subject, subjIndex, cat, object, objectIndex, argNum);
  }

  private CcgParser parseLexicon(String[] lexicon, String[] unknownLexicon, String[] binaryRuleArray,
      String[] unaryRuleArray, double[] weights, double[] unknownWeights, boolean allowComposition,
      boolean allowWordSkipping, boolean normalFormOnly, boolean useStringLexicon) {
    Preconditions.checkArgument(lexicon.length == weights.length);
    List<CcgCategory> categories = Lists.newArrayList();
    Set<HeadedSyntacticCategory> syntacticCategories = Sets.newHashSet();
    Set<List<String>> words = Sets.newHashSet();
    Set<String> semanticPredicates = Sets.newHashSet();
    for (int i = 0; i < lexicon.length; i++) {
      int commaInd = lexicon[i].indexOf(",");
      words.add(Arrays.asList(lexicon[i].substring(0, commaInd)));

      CcgCategory category = CcgCategory.parseFrom(lexicon[i].substring(commaInd + 1));
      categories.add(category);
      for (String head : Iterables.concat(category.getAssignment())) {
        semanticPredicates.addAll(Arrays.asList(head));
      }
      semanticPredicates.addAll(category.getSubjects());
      syntacticCategories.add(category.getSyntax());
    }

    // Parse unknown word lexicon entries.
    for (int i = 0; i < unknownLexicon.length; i++) {
      int commaInd = unknownLexicon[i].indexOf(",");

      CcgCategory category = CcgCategory.parseFrom(unknownLexicon[i].substring(commaInd + 1));
      categories.add(category);
      for (String head : Iterables.concat(category.getAssignment())) {
        semanticPredicates.addAll(Arrays.asList(head));
      }
      semanticPredicates.addAll(category.getSubjects());
      syntacticCategories.add(category.getSyntax());
    }

    // Parse the binary rules
    List<CcgBinaryRule> binaryRules = Lists.newArrayList();
    for (int i = 0; i < binaryRuleArray.length; i++) {
      CcgBinaryRule rule = CcgBinaryRule.parseFrom(binaryRuleArray[i]);
      binaryRules.add(rule);
      semanticPredicates.addAll(Arrays.asList(rule.getSubjects()));
      syntacticCategories.add(rule.getLeftSyntacticType().getCanonicalForm());
      syntacticCategories.add(rule.getRightSyntacticType().getCanonicalForm());
      syntacticCategories.add(rule.getParentSyntacticType().getCanonicalForm());
    }

    List<CcgUnaryRule> unaryRules = Lists.newArrayList();
    for (int i = 0; i < unaryRuleArray.length; i++) {
      CcgUnaryRule rule = CcgUnaryRule.parseFrom(unaryRuleArray[i]);
      unaryRules.add(rule);
      semanticPredicates.addAll(rule.getSubjects());
      syntacticCategories.add(rule.getInputSyntacticCategory().getCanonicalForm());
      syntacticCategories.add(rule.getResultSyntacticCategory().getCanonicalForm());
    }
    syntacticCategories.add(HeadedSyntacticCategory.parseFrom("Unknown{0}"));
    semanticPredicates.add("special:string");
    semanticPredicates.add("special:unknown");

    // Build the terminal distribution.
    DiscreteVariable ccgCategoryType = new DiscreteVariable("ccgCategory", categories);
    DiscreteVariable wordType = new DiscreteVariable("words", words);
    DiscreteVariable posType = new DiscreteVariable("pos",
        Lists.newArrayList(DEFAULT_POS, "NN", "JJ"));

    terminalVar = VariableNumMap.singleton(0, "words", wordType);
    posTagVar = VariableNumMap.singleton(1, "posTag", posType);
    ccgCategoryVar = VariableNumMap.singleton(2, "ccgCategory", ccgCategoryType);
    VariableNumMap vars = VariableNumMap.unionAll(terminalVar, ccgCategoryVar);
    TableFactorBuilder terminalBuilder = new TableFactorBuilder(vars, SparseTensorBuilder.getFactory());
    for (int i = 0; i < lexicon.length; i++) {
      int commaInd = lexicon[i].indexOf(",");
      List<String> wordList = Arrays.asList(lexicon[i].substring(0, commaInd));
      CcgCategory category = CcgCategory.parseFrom(lexicon[i].substring(commaInd + 1));
      terminalBuilder.setWeight(vars.outcomeArrayToAssignment(wordList, category), weights[i]);
    }
    
    // Build the unknown word terminal distribution
    VariableNumMap unknownVars = VariableNumMap.unionAll(posTagVar, ccgCategoryVar);
    TableFactorBuilder unknownTerminalBuilder = new TableFactorBuilder(unknownVars,
        SparseTensorBuilder.getFactory());
    for (int i = 0; i < unknownLexicon.length; i++) {
      int commaInd = unknownLexicon[i].indexOf(",");
      String posTag = unknownLexicon[i].substring(0, commaInd);
      CcgCategory category = CcgCategory.parseFrom(unknownLexicon[i].substring(commaInd + 1));
      unknownTerminalBuilder.setWeight(unknownVars.outcomeArrayToAssignment(posTag, category), unknownWeights[i]);
    }

    // Distribution over CCG combinators, i.e., binary combination rules.
    DiscreteVariable syntaxType = new DiscreteVariable("syntacticCategories", CcgGrammarUtils.getSyntacticCategoryClosure(syntacticCategories));
    DiscreteFactor syntaxDistribution = CcgGrammarUtils.buildUnrestrictedBinaryDistribution(syntaxType, binaryRules, allowComposition);
    VariableNumMap leftSyntaxVar = syntaxDistribution.getVars().getVariablesByName(CcgParser.LEFT_SYNTAX_VAR_NAME);
    VariableNumMap rightSyntaxVar = syntaxDistribution.getVars().getVariablesByName(CcgParser.RIGHT_SYNTAX_VAR_NAME);
    VariableNumMap inputSyntaxVars = leftSyntaxVar.union(rightSyntaxVar);
    VariableNumMap parentSyntaxVar = syntaxDistribution.getVars().getVariablesByName(CcgParser.PARENT_SYNTAX_VAR_NAME);

    Preconditions.checkState(syntacticCombinations.length == syntacticCombinationWeights.length);
    for (int i = 0; i < syntacticCombinations.length; i++) {
      HeadedSyntacticCategory leftSyntax = HeadedSyntacticCategory.parseFrom(syntacticCombinations[i][0]).getCanonicalForm();
      HeadedSyntacticCategory rightSyntax = HeadedSyntacticCategory.parseFrom(syntacticCombinations[i][1]).getCanonicalForm();
      DiscreteFactor combinationFactor = TableFactor.pointDistribution(inputSyntaxVars,
          inputSyntaxVars.outcomeArrayToAssignment(leftSyntax, rightSyntax)).product(syntacticCombinationWeights[i]);

      syntaxDistribution = syntaxDistribution.add(syntaxDistribution.product(combinationFactor));
    }
    
    // Build the dependency distribution.
    DiscreteVariable semanticPredicateType = new DiscreteVariable("semanticPredicates", semanticPredicates);
    DiscreteVariable argumentNums = new DiscreteVariable("argNums", Ints.asList(0, 1, 2, 3));
    semanticHeadVar = VariableNumMap.singleton(0, "semanticHead", semanticPredicateType);
    semanticSyntaxVar = VariableNumMap.singleton(1, "semanticSyntax", syntaxType);
    semanticArgNumVar = VariableNumMap.singleton(2, "semanticArgNum", argumentNums);
    semanticArgVar = VariableNumMap.singleton(3, "semanticArg", semanticPredicateType);
    semanticHeadPosVar = VariableNumMap.singleton(4, "semanticHeadPos", posType);
    semanticArgPosVar = VariableNumMap.singleton(5, "semanticArgPos", posType);
    vars = VariableNumMap.unionAll(semanticHeadVar, semanticSyntaxVar, semanticArgNumVar, semanticArgVar,
        semanticHeadPosVar, semanticArgPosVar);

    TableFactorBuilder dependencyFactorBuilder = new TableFactorBuilder(vars, SparseTensorBuilder.getFactory());
    Preconditions.checkState(dependencyCombinations.length == dependencyWeightIncrements.length);
    for (int i = 0; i < dependencyCombinations.length; i++) {
      HeadedSyntacticCategory cat = HeadedSyntacticCategory.parseFrom(dependencyCombinations[i][1]).getCanonicalForm();
      int argNum = Integer.parseInt(dependencyCombinations[i][2]);
      dependencyFactorBuilder.setWeight(vars.outcomeArrayToAssignment(
          dependencyCombinations[i][0], cat, argNum, dependencyCombinations[i][3],
          dependencyCombinations[i][4], dependencyCombinations[i][5]), Math.log(1 + dependencyWeightIncrements[i]));
    }
    TableFactor dependencyFactor = dependencyFactorBuilder.buildSparseInLogSpace();

    // Distribution over unary rules.
    DiscreteFactor unaryRuleDistribution = CcgGrammarUtils.buildUnaryRuleDistribution(unaryRules,
        leftSyntaxVar.getDiscreteVariables().get(0));
    VariableNumMap unaryRuleInputVar = unaryRuleDistribution.getVars().getVariablesByName(CcgParser.UNARY_RULE_INPUT_VAR_NAME);
    VariableNumMap unaryRuleVar = unaryRuleDistribution.getVars().getVariablesByName(CcgParser.UNARY_RULE_VAR_NAME);

    DiscreteFactor compiledSyntaxDistribution = CcgGrammarUtils.compileUnaryAndBinaryRules(unaryRuleDistribution,
        syntaxDistribution, syntaxType);
    VariableNumMap searchMoveVar = compiledSyntaxDistribution.getVars().getVariablesByName(
        CcgParser.PARENT_MOVE_SYNTAX_VAR_NAME);

    // Distribution over the root of the tree.
    DiscreteFactor rootDistribution = TableFactor.unity(leftSyntaxVar);
    Assignment assignment = leftSyntaxVar.outcomeArrayToAssignment(HeadedSyntacticCategory.parseFrom("S[b]{0}"));
    rootDistribution = rootDistribution.add(TableFactor.pointDistribution(leftSyntaxVar, assignment));
    assignment = leftSyntaxVar.outcomeArrayToAssignment(HeadedSyntacticCategory.parseFrom("S[ng]{0}"));
    rootDistribution = rootDistribution.add(TableFactor.pointDistribution(leftSyntaxVar, assignment));
    
    VariableNumMap headedRootPredicateVar = VariableNumMap.singleton(3, "headedRootSemanticPredicate", semanticPredicateType);
    VariableNumMap headedRootPosVar = VariableNumMap.singleton(4, "headedRootPos", posType);
    VariableNumMap headedRootVars = VariableNumMap.unionAll(leftSyntaxVar, headedRootPredicateVar, headedRootPosVar);
    TableFactorBuilder headedRootBuilder = new TableFactorBuilder(headedRootVars, SparseTensorBuilder.getFactory());
    headedRootBuilder.setWeight(Math.log(2.0), HeadedSyntacticCategory.parseFrom("N{0}"), "apple", DEFAULT_POS);
    DiscreteFactor headedRootDistribution = headedRootBuilder.buildSparseInLogSpace();

    // Distribution over pos tags and terminal syntactic types,
    // for smoothing sparse word counts.
    terminalSyntaxVar = VariableNumMap.singleton(3, "terminalSyntax", leftSyntaxVar.getDiscreteVariables().get(0));
    VariableNumMap terminalPosVars = posTagVar.union(terminalSyntaxVar);
    DiscreteFactor posDistribution = TableFactor.unity(terminalPosVars);
    HeadedSyntacticCategory cat = HeadedSyntacticCategory.parseFrom("N{0}");
    posDistribution = posDistribution.add(TableFactor.pointDistribution(terminalPosVars, 
        terminalPosVars.outcomeArrayToAssignment("NN", cat)));

    // Distribution over syntactic categories assigned to each word.
    VariableNumMap terminalSyntaxVars = terminalVar.union(terminalSyntaxVar);
    DiscreteFactor terminalSyntaxDistribution = TableFactor.unity(terminalSyntaxVars);
     terminalSyntaxDistribution = terminalSyntaxDistribution.add(TableFactor.pointDistribution(
        terminalSyntaxVars, terminalSyntaxVars.outcomeArrayToAssignment(Arrays.asList("i"),
            HeadedSyntacticCategory.parseFrom("N{0}"))).product(2.0));
    terminalSyntaxDistribution = terminalSyntaxDistribution.add(TableFactor.pointDistribution(
        terminalSyntaxVars, terminalSyntaxVars.outcomeArrayToAssignment(Arrays.asList("blue"),
            HeadedSyntacticCategory.parseFrom("N{0}"))).product(2.0));
 
    // Distribution over predicate-argument distances.
    VariableNumMap distancePredicateVars = VariableNumMap.unionAll(semanticHeadVar, semanticSyntaxVar, semanticArgNumVar, semanticHeadPosVar);
    VariableNumMap wordDistanceVar = VariableNumMap.singleton(6, "wordDistance", CcgParser.wordDistanceVarType);
    VariableNumMap puncDistanceVar = VariableNumMap.singleton(6, "puncDistance", CcgParser.puncDistanceVarType);
    VariableNumMap verbDistanceVar = VariableNumMap.singleton(6, "verbDistance", CcgParser.verbDistanceVarType);
    DiscreteFactor puncDistanceFactor = TableFactor.logUnity(distancePredicateVars.union(puncDistanceVar));
    DiscreteFactor verbDistanceFactor = TableFactor.logUnity(distancePredicateVars.union(verbDistanceVar));
    Set<String> puncTagSet = ParametricCcgParser.DEFAULT_PUNC_TAGS;
    Set<String> verbTagSet = ParametricCcgParser.DEFAULT_VERB_TAGS;

    VariableNumMap wordDistanceVars = distancePredicateVars.union(wordDistanceVar);
    TableFactorBuilder wordFactorIncrementBuilder = new TableFactorBuilder(wordDistanceVars, SparseTensorBuilder.getFactory());
    HeadedSyntacticCategory eatCategory = HeadedSyntacticCategory.parseFrom("((S[b]{0}\\N{1}){0}/N{2}){0}").getCanonicalForm();
    HeadedSyntacticCategory tastyCategory = HeadedSyntacticCategory.parseFrom("(N{1}/N{1}){0}").getCanonicalForm();
    wordFactorIncrementBuilder.incrementWeight(wordDistanceVars.outcomeArrayToAssignment("eat", eatCategory, 2, DEFAULT_POS, 0), Math.log(4.0));
    wordFactorIncrementBuilder.incrementWeight(wordDistanceVars.outcomeArrayToAssignment("eat", eatCategory, 2, DEFAULT_POS, 1), Math.log(3.0));
    wordFactorIncrementBuilder.incrementWeight(wordDistanceVars.outcomeArrayToAssignment("eat", eatCategory, 2, DEFAULT_POS, 2), Math.log(2.0));
    wordFactorIncrementBuilder.incrementWeight(wordDistanceVars.outcomeArrayToAssignment("tasty", tastyCategory, 1, "JJ", 0), Math.log(2.0));
    DiscreteFactor wordDistanceFactor = wordFactorIncrementBuilder.buildSparseInLogSpace();

    // Create a distribution over headed binary rules.
    int maxVarNum = Ints.max(syntaxDistribution.getVars().getVariableNumsArray());
    VariableNumMap headedBinaryRulePredicateVar = VariableNumMap.singleton(maxVarNum + 1, 
        "headedBinaryRulePredicate", semanticPredicateType); 
    VariableNumMap headedBinaryRulePosVar = VariableNumMap.singleton(maxVarNum + 2, 
        "headedBinaryRulePos", posType);
    VariableNumMap syntaxVars = VariableNumMap.unionAll(leftSyntaxVar, rightSyntaxVar);
    VariableNumMap binaryHeadVars = VariableNumMap.unionAll(headedBinaryRulePredicateVar, headedBinaryRulePosVar);
    VariableNumMap headedBinaryVars = VariableNumMap.unionAll(leftSyntaxVar, rightSyntaxVar,
        parentSyntaxVar, headedBinaryRulePredicateVar, headedBinaryRulePosVar);
    TableFactorBuilder headedBinaryFactorBuilder = new TableFactorBuilder(headedBinaryVars, SparseTensorBuilder.getFactory());
    HeadedSyntacticCategory nounCategory = HeadedSyntacticCategory.parseFrom("N{0}").getCanonicalForm();
    Assignment tastyAssignment = syntaxVars.outcomeArrayToAssignment(tastyCategory, nounCategory);
    Iterator<Outcome> possibleRuleIter = syntaxDistribution.conditional(tastyAssignment).outcomeIterator();
    while (possibleRuleIter.hasNext()) {
      Assignment ruleAssignment = possibleRuleIter.next().getAssignment().union(tastyAssignment);
      Assignment predicateAssignmentDefault = binaryHeadVars.outcomeArrayToAssignment("apple", DEFAULT_POS);
      Assignment predicateAssignmentJj = binaryHeadVars.outcomeArrayToAssignment("berries", "JJ");
      headedBinaryFactorBuilder.incrementWeight(ruleAssignment.union(predicateAssignmentDefault), Math.log(2.0));
      headedBinaryFactorBuilder.incrementWeight(ruleAssignment.union(predicateAssignmentJj), Math.log(3.0));
    }
    DiscreteFactor headedBinaryRuleFactor = headedBinaryFactorBuilder.buildSparseInLogSpace();

    CcgLexicon lexiconFactor = new TableLexicon(terminalVar, ccgCategoryVar,
        terminalBuilder.build());
    CcgLexicon unknownWordLexicon = new UnknownWordLexicon(terminalVar, posTagVar,
        ccgCategoryVar, unknownTerminalBuilder.build());

    List<CcgLexicon> lexicons = Lists.newArrayList();
    if (allowWordSkipping) {
      lexicons.add(new SkipLexicon(lexiconFactor, TableFactor.unity(terminalVar)));
      lexicons.add(new SkipLexicon(unknownWordLexicon, TableFactor.unity(terminalVar)));
    } else {
      lexicons.add(lexiconFactor);
      lexicons.add(unknownWordLexicon);
    }

    if (useStringLexicon) {
      CcgCategory stringCategory = CcgCategory.parseFrom("String{0},(lambda ($0) $0),0 special:string");
      CcgCategory unknownCategory = CcgCategory.parseFrom("Unknown{0},(lambda ($0) unknownCommand),0 special:unknown");
      CcgLexicon stringLexicon = new StringLexicon(terminalVar, Arrays.asList(stringCategory, unknownCategory),
          Arrays.asList(CategorySpanConfig.ALL_SPANS, CategorySpanConfig.WHOLE_SENTENCE),
          StringLexicon.getDefaultDetokenizer());
      lexicons.add(stringLexicon);
    }
    
    List<LexiconScorer> scorers = Lists.newArrayList();
    scorers.add(new SupertagLexiconScorer("supertags"));
    scorers.add(new SyntaxLexiconScorer(terminalVar, posTagVar, terminalSyntaxVar, posDistribution,
        terminalSyntaxDistribution));

    return new CcgParser(lexicons, scorers, semanticHeadVar, semanticSyntaxVar,
        semanticArgNumVar, semanticArgVar, semanticHeadPosVar, semanticArgPosVar, dependencyFactor,
        wordDistanceVar, wordDistanceFactor, puncDistanceVar, puncDistanceFactor, puncTagSet,
        verbDistanceVar, verbDistanceFactor, verbTagSet,
        leftSyntaxVar, rightSyntaxVar, parentSyntaxVar, syntaxDistribution, unaryRuleInputVar,
        unaryRuleVar, unaryRuleDistribution, headedBinaryRulePredicateVar, headedBinaryRulePosVar,
        headedBinaryRuleFactor, searchMoveVar, compiledSyntaxDistribution, leftSyntaxVar,
        headedRootPredicateVar, headedRootPosVar, rootDistribution, headedRootDistribution, normalFormOnly);
  }

  private static class TestChartFilter implements ChartCost {

    @Override
    public double apply(ChartEntry entry, int spanStart, int spanEnd,
        int sentenceLength, DiscreteVariable syntaxVarType) {
      if (spanStart == 3 && spanEnd == 5) {
        HeadedSyntacticCategory syntax = (HeadedSyntacticCategory) syntaxVarType.getValue(entry.getHeadedSyntax());
        return syntax.getSyntax().equals(SyntacticCategory.parseFrom("N\\N")) ? 0.0 : Double.NEGATIVE_INFINITY;
      }
      return 0.0;
    }
  }
}
