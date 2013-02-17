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
import com.google.common.collect.Sets;
import com.google.common.io.NullOutputStream;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.ccg.CcgChart.ChartEntry;
import com.jayantkrish.jklol.ccg.CcgChart.ChartFilter;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteFactor.Outcome;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.training.NullLogFunction;
import com.jayantkrish.jklol.util.Assignment;

public class CcgParserTest extends TestCase {

  CcgParser parser, parserWithComposition, parserWithUnary, parserWithUnaryAndComposition;
  
  ExpressionParser exp;
  
  private static final String[] lexicon = {"i,N{0},i,0 i", 
    "people,N{0},people,0 people", 
    "berries,N{0},berries,0 berries", 
    "houses,N{0},houses,0 houses",
    "eat,((S[b]{0}\\N{1}){0}/N{2}){0},(lambda $2 $1 (exists a b (and ($1 a) ($2 b) (eat a b)))),0 eat,eat 1 1,eat 2 2", 
    "that,((N{1}\\N{1}){0}/(S[0]{2}\\N{1}){2}){0},(lambda $2 $1 (lambda x (and ($1 x) ($2 (lambda y (equals y x)))))),0 that,that 1 1,that 2 2",
    "that,((N{1}\\N{1}){0}/(S[0]{2}/N{3}){2}){0},(lambda $2 $1 (lambda x (and ($1 x) ($2 (lambda y (equals y x)))))),0 that,that 1 1,that 2 2",
    "quickly,(((S[1]{1}\\N{2}){1}/N{3}){1}/((S[1]{1}\\N{2}){1}/N{3}){1}){0},,0 quickly,quickly 1 1", 
    "in,((N{1}\\N{1}){0}/N{2}){0},(lambda $2 $1 (lambda c (exists d (and ($1 c) ($2 d) (in c d))))),0 in,in 1 1,in 2 2",
    "amazingly,((N{1}/N{1}){2}/(N{1}/N{1}){2}){0},,0 amazingly,amazingly 1 2",
    "tasty,(N{1}/N{1}){0},(lambda $1 (lambda e (and (tasty e) ($1 e)))),0 tasty,tasty 1 1",
    "in,(((S[1]{1}\\N{2}){1}\\(S[1]{1}\\N{2}){1}){0}/N{3}){0},,0 in,in 1 1,in 2 3",
    "and,((N{1}\\N{1}){0}/N{1}){0},,0 and",
    "almost,(((N{1}\\N{1}){2}/N{3}){2}/((N{1}\\N{1}){2}/N{3}){2}){0},,0 almost,almost 1 2",
    "is,((S[b]{0}\\N{1}){0}/N{2}){0},,0 is,is 1 1, is 2 2", 
    "directed,((S[b]{0}\\N{1}){0}/N{2}){0},,0 directed,directed 1 2,directed 2 1",
    ";,;{0},;,0 ;", "or,conj{0},word:or,0 or",
    "about,(NP{0}/(S[1]{1}\\N{2}){1}){0},,0 about,about 1 1", 
    "eating,((S[ng]{0}\\N{1}){0}/N{2}){0},,0 eat,eat 1 1,eat 2 2",
    "rapidly,((S[1]{1}\\N{2}){1}/(S[1]{1}\\N{2}){1}){0},,0 rapidly,rapidly 1 1",
    "colorful,(N{1}/N{1}){0},(lambda $1 (lambda e (and (colorful e) ($1 e)))),0 colorful,colorful 1 1",
    "*not_a_word*,(NP{0}/N{1}){0},,0 *not_a_word*",
    "near,((S[1]{1}/(S[1]{1}\\N{0}){1}){0}/N{2}){0},,0 near,near 2 2",
    "the,(N{0}/N{0}){1},,1 the,the 1 0",
    "exactly,(S[1]{1}/S[1]{1}){0},,0 exactly,exactly 1 1",
    "green,(N{0}/N{0}){1},,1 green,green_(N{0}/N{0}){1} 1 0"};
  
  private static final double[] weights = {0.5, 1.0, 1.0, 1.0, 
    0.3, 1.0, 1.0, 
    1.0, 1.0,
    1.0, 1.0,
    0.5, 1.0, 2.0,
    0.25, 1.0,
    1.0, 0.5,
    1.0, 1.0,
    0.5, 1.0,
    1.0, 1.0, 1.0, 1.0, 1.0};

  private static final String[] binaryRuleArray = {";{1} N{0} N{0}", "N{0} ;{1} N{0},(lambda $L $R $L)", 
    ";{2} (S[0]{0}\\N{1}){0} (N{0}\\N{1}){0}", "\",{2} N{0} (N{0}\\N{0}){1}\"", 
    "conj{1} N{0} (N{0}\\N{0}){1},(lambda $L $R (lambda $0 (lambda x (forall (pred (set $R $0)) (pred x)))))",  
    "conj{2} (S[0]{0}\\N{1}){0} ((S[0]{0}\\N{1}){0}\\(S[0]{0}\\N{1}){0}){2}",
    "\"N{0} N{1} N{1}\",\"(lambda $L $R (lambda j (exists k (and ($L k) ($R j) (special:compound k j)))))\",\"special:compound 1 0\",\"special:compound 2 1\""};

  private static final String[] unaryRuleArray = {"N{0} (S[1]{1}/(S[1]{1}\\N{0}){1}){1}",
    "N{0} (N{1}/N{1}){0}", "((S[0]{0}\\N{1}){0}/N{2}){0} ((S[0]{0}/NP{1}){0}/NP{2}){0}" };
  
  // Syntactic CCG weights to set. All unlisted combinations get weight 1.0, all
  // listed combinations get weight 1.0 + given weight
  private static final String[][] syntacticCombinations = {
    {"(N{1}\\N{1}){0}/(S[0]{2}\\N{1}){2}){0}", "(S[b]{2}\\N{1}){2}"},
    {"(NP{0}/(S[1]{1}\\N{2}){1}){0}", "((S[ng]{0}\\N{1}){0}/N{2}){0}"},
  };
  private static final double[] syntacticCombinationWeights = {
    2.0, -0.75
  };
  
  private VariableNumMap terminalVar;
  private VariableNumMap ccgCategoryVar;
  
  private VariableNumMap posTagVar;
  private VariableNumMap terminalSyntaxVar;
  
  private VariableNumMap semanticHeadVar;
  private VariableNumMap semanticArgNumVar;
  private VariableNumMap semanticArgVar;

  public void setUp() {
    parser = parseLexicon(lexicon, binaryRuleArray, new String[] {"FOO{0} FOO{0}"}, weights, false);
    parserWithComposition = parseLexicon(lexicon, binaryRuleArray, new String[] {"FOO{0} FOO{0}"}, weights, true);
    parserWithUnary = parseLexicon(lexicon, binaryRuleArray, unaryRuleArray, weights, false);
    parserWithUnaryAndComposition = parseLexicon(lexicon, binaryRuleArray,
        new String[] {"N{0} (S[1]{1}/(S[1]{1}\\N{0}){1}){1},(lambda $0 (lambda $1 ($1 $0)))"}, weights, true);
    
    exp = new ExpressionParser();
  }
  
  public void testParse() {
    List<CcgParse> parses = parser.beamSearch(Arrays.asList("I", "quickly", "eat", "amazingly", "tasty", "berries"), 20);
    assertEquals(1, parses.size());
    CcgParse parse = parses.get(0);

    assertEquals(2.0, parse.getNodeProbability());
    assertEquals(4.0, parse.getRight().getNodeProbability());
    System.out.println(parse.getRight().getLeft());
    assertEquals(4.0, parse.getRight().getLeft().getNodeProbability());
    assertEquals(0.5, parse.getLeft().getNodeProbability());
    assertEquals(0.5 * 0.3 * 4.0 * 4.0 * 2.0, parse.getSubtreeProbability());
    
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
  
  public void testParse2() {
    List<CcgParse> parses = parser.beamSearch(
        Arrays.asList("people", "that", "quickly", "eat", "berries", "in", "houses"), 10);
    
    for (CcgParse parse : parses) {
      System.out.println(parse);
      System.out.println(parse.getAllDependencies());
    }
    
    assertEquals(3, parses.size());

    System.out.println(parses.get(0).getSubtreeProbability() + " " + parses.get(0));
    System.out.println(parses.get(0).getAllDependencies());
    System.out.println(parses.get(1).getSubtreeProbability() + " " + parses.get(1));
    System.out.println(parses.get(1).getAllDependencies());
    System.out.println(parses.get(2).getSubtreeProbability() + " " + parses.get(2));
    System.out.println(parses.get(2).getAllDependencies());
    
    // The parse where "in" attaches to "people" should have higher probability.
    CcgParse parse = parses.get(0);
    assertEquals("in", parse.getNodeDependencies().get(0).getHead());
    assertEquals(1, parse.getNodeDependencies().get(0).getArgIndex());
    assertEquals("people", parse.getNodeDependencies().get(0).getObject());
    assertEquals(0.3 * 4 * 2 * 2 * 3 * 4, parse.getSubtreeProbability());
    assertEquals("people", Iterables.getOnlyElement(parse.getSemanticHeads()).getHead());

    parse = parses.get(1);
    assertEquals(2, parse.getNodeDependencies().size());
    // Parse should have "people" as an arg1 dependency for both "that" and "eat"
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
  
  public void testParse3() {
    List<CcgParse> parses = parser.beamSearch(
        Arrays.asList("green", "people"), 10);
    
    assertEquals(1, parses.size());
    assertEquals(2.0, parses.get(0).getSubtreeProbability());
  }
  
  public void testParseLogicalFormApplication() {
    List<CcgParse> parses = parser.beamSearch(Arrays.asList(
       "i", "quickly", "eat", "berries"), 10);
    
    assertEquals(1, parses.size());
    
    Expression expectedLf = exp.parseSingleExpression("(exists a b (and (i a) (berries b) (eat a b)))");
    assertEquals(expectedLf, parses.get(0).getLogicalForm().simplify());

    for (CcgParse parse : parses) {
      System.out.println(parse);
      System.out.println(parse.getAllDependencies());
      Expression lf = parse.getLogicalForm();
      if (lf != null) {
        System.out.println("lf: " + lf);
        System.out.println("simple lf: " + lf.simplify());
      }
    }
  }
      
  public void testParseLogicalFormApplication2() {
    List<CcgParse> parses = parser.beamSearch(Arrays.asList(
        "i", "that", "eat", "berries"), 10);
    
    assertEquals(1, parses.size());

    Expression expectedLf = exp.parseSingleExpression("(lambda x (exists a b (and (i x) (equals a x) (berries b) (eat a b))))");    
    for (CcgParse parse : parses) {
      System.out.println(parse);
      System.out.println(parse.getAllDependencies());
      System.out.println(parse.getLogicalForm().simplify());
      assertTrue(expectedLf.functionallyEquals(parse.getLogicalForm().simplify()));
    }
  }

  public void testParseLogicalFormComposition() {
    List<CcgParse> parses = parserWithComposition.beamSearch(Arrays.asList(
        "the", "colorful", "tasty"), 10);
    assertEquals(8, parses.size());
    
    Expression expectedLf = exp.parseSingleExpression("(lambda $1 (lambda e (and (colorful e) (tasty e) ($1 e))))");    
    for (CcgParse parse : parses) {
      System.out.println(parse);
      System.out.println(parse.getAllDependencies());
      System.out.println(parse.getLogicalForm().simplify());
      assertTrue(expectedLf.functionallyEquals(parse.getLogicalForm().simplify()));
    }
  }
  
  public void testParseLogicalFormBinaryRule() {
    List<CcgParse> parses = parserWithComposition.beamSearch(Arrays.asList(
        ";", "berries"), 10);
    assertEquals(1, parses.size());
    assertEquals(null, parses.get(0).getLogicalForm());
  }
  
  public void testParseLogicalFormBinaryRule2() {
    List<CcgParse> parses = parserWithComposition.beamSearch(Arrays.asList(
        "berries", ";"), 10);
    assertEquals(1, parses.size());
    Expression expectedLf = exp.parseSingleExpression("berries");
    assertEquals(expectedLf, parses.get(0).getLogicalForm().simplify());
  }
  
  public void testParseLogicalFormBinaryRule3() {
    List<CcgParse> parses = parserWithComposition.beamSearch(Arrays.asList(
        "people", "berries"), 10);
    assertEquals(1, parses.size());
    Expression expectedLf = exp.parseSingleExpression("(lambda j (exists k (and (people k) (berries j) (special:compound k j))))");
    assertEquals(expectedLf, parses.get(0).getLogicalForm().simplify());
  }
  
  public void testParseLogicalFormConjunction() {
    List<CcgParse> parses = parserWithComposition.beamSearch(Arrays.asList(
        "i", "eat", "people", "or", "berries"), 10);
    assertEquals(1, parses.size());
    System.out.println(parses.get(0));
    Expression expectedLf = exp.parseSingleExpression("(exists a b (and (i a) (forall (pred (set berries people)) (pred b)) (eat a b)))");
    assertEquals(expectedLf, parses.get(0).getLogicalForm().simplify());
  }

  public void testParseLogicalFormUnary() {
    List<CcgParse> parses = parserWithUnaryAndComposition.beamSearch(Arrays.asList(
        "berries", "that", "i", "eat"), 10);

    assertEquals(2, parses.size());
    Expression expectedLf = exp.parseSingleExpression("(lambda x (exists a b (and (berries x) (i a) (equals b x) (eat a b))))");
    boolean foundN = false;
    for (CcgParse parse : parses) {
      System.out.println(parse);
      System.out.println(parse.getAllDependencies());
      String root = parse.getSyntacticCategory().getValue();
      if (root != null && root.equals("N")) {
        assertTrue(expectedLf.functionallyEquals(parse.getLogicalForm().simplify()));
        System.out.println(parse.getLogicalForm().simplify());
        foundN = true;
      }
    }
    assertTrue(foundN);
  }

  public void testParseComposition() {
    assertEquals(0, parser.beamSearch(Arrays.asList("rapidly", "eat"), 10).size());
    
    List<CcgParse> parses = parserWithComposition.beamSearch(Arrays.asList("rapidly", "eat"), 10);

    assertEquals(1, parses.size());
    CcgParse parse = parses.get(0);
    System.out.println(parse);
    
    Set<DependencyStructure> deps = Sets.newHashSet(parse.getAllDependencies());
    Set<DependencyStructure> expectedDeps = Sets.newHashSet(
        new DependencyStructure("rapidly", 0, "eat", 1, 1));
    assertEquals(expectedDeps, deps);
    
    SyntacticCategory expectedSyntax = SyntacticCategory.parseFrom("(S[b]\\N)/N");
    assertEquals(expectedSyntax, parse.getSyntacticCategory());
  }

  public void testParseComposition2() {
    assertEquals(0, parser.beamSearch(Arrays.asList("eat", "amazingly", "tasty"), 10).size());
    
    List<CcgParse> parses = parserWithComposition.beamSearch(Arrays.asList("eat", "amazingly", "tasty"), 10);

    assertEquals(1, parses.size());
    CcgParse parse = parses.get(0);
    
    Set<DependencyStructure> deps = Sets.newHashSet(parse.getAllDependencies());
    Set<DependencyStructure> expectedDeps = Sets.newHashSet(
        new DependencyStructure("amazingly", 1, "tasty", 2, 1));
    assertEquals(expectedDeps, deps);
    
    SyntacticCategory expectedSyntax = SyntacticCategory.parseFrom("(S[b]\\N)/N");
    assertEquals(expectedSyntax, parse.getSyntacticCategory());
    System.out.println(parse.getHeadedSyntacticCategory());
    
    Set<IndexedPredicate> heads = parse.getSemanticHeads();
    assertEquals(1, heads.size());
    assertEquals("eat", Iterables.getOnlyElement(heads).getHead());
  }
  
  public void testParseComposition3() {
    assertEquals(1, parser.beamSearch(Arrays.asList("about", "eating", "berries"), 10).size());
    
    List<CcgParse> parses = parserWithComposition.beamSearch(Arrays.asList("about", "eating", "berries"), 10);
    
    for (CcgParse parse : parses) {
      System.out.println(parse);
      System.out.println(parse.getAllDependencies());
    }

    assertEquals(2, parses.size());
    for (CcgParse parse : parses) {    
      Set<DependencyStructure> deps = Sets.newHashSet(parse.getAllDependencies());
      Set<DependencyStructure> expectedDeps = Sets.newHashSet(
          new DependencyStructure("about", 0, "eat", 1, 1),
          new DependencyStructure("eat", 1, "berries", 2, 2));
      assertEquals(expectedDeps, deps);

      HeadedSyntacticCategory expectedSyntax = HeadedSyntacticCategory.parseFrom("NP{0}");
      assertEquals(expectedSyntax, parse.getHeadedSyntacticCategory());

      Set<IndexedPredicate> heads = parse.getSemanticHeads();
      assertEquals(1, heads.size());
      assertEquals("about", Iterables.getOnlyElement(heads).getHead());
    }
    
    assertEquals(2.0 * 4, parses.get(0).getSubtreeProbability());
    assertEquals(0.5 * 4, parses.get(1).getSubtreeProbability());
  }
  
  public void testParseComposition4() {
    List<CcgParse> parses = parserWithComposition.beamSearch(
        Arrays.asList("i", "quickly", "eat", "amazingly", "tasty", "berries"), 20);
    assertEquals(3, parses.size());
    
    Set<DependencyStructure> expectedDeps = Sets.newHashSet(
        new DependencyStructure("eat", 2, "i", 0, 1),
        new DependencyStructure("eat", 2, "berries", 5, 2),
        new DependencyStructure("quickly", 1, "eat", 2, 1),
        new DependencyStructure("amazingly", 3, "tasty", 4, 1),
        new DependencyStructure("tasty", 4, "berries", 5, 1));

    for (CcgParse parse : parses) {
      assertEquals(expectedDeps, Sets.newHashSet(parse.getAllDependencies()));
    }
  }
  
  public void testParseComposition5() {
    List<CcgParse> parses = parserWithComposition.beamSearch(
        Arrays.asList("exactly", "eat"), 10);
    assertEquals(1, parses.size());
    
    assertEquals(HeadedSyntacticCategory.parseFrom("((S[b]{0}\\N{1}){0}/N{2}){0}"),
        parses.get(0).getHeadedSyntacticCategory());
  }

  public void testParseHeadUnification() {
    List<CcgParse> parses = parser.beamSearch(
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
    List<CcgParse> parses = parser.beamSearch(
        Arrays.asList("people", "almost", "in", "houses"), 10);
    
    assertEquals(1, parses.size());
    
    CcgParse parse = parses.get(0);
    System.out.println(parse.getAllDependencies());
  }
  
  public void testSubjectPatterns() {
    List<CcgParse> parses = parser.beamSearch(
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
  
  public void testParseUnfilledDep() {
    List<CcgParse> parses = parser.beamSearch(
        Arrays.asList("about", "eating", "berries"), 10);
    
    assertEquals(1, parses.size());
    
    CcgParse parse = parses.get(0);
    Set<DependencyStructure> expectedDeps = Sets.newHashSet(
        new DependencyStructure("about", 0, "eat", 1, 1),
        new DependencyStructure("eat", 1, "berries", 2, 2));
    assertEquals(expectedDeps, Sets.newHashSet(parse.getAllDependencies()));
  }
  
  public void testBinaryRules1() {
    List<CcgParse> parses = parser.beamSearch(
        Arrays.asList("berries", ";"), 10);

    assertEquals(1, parses.size());
    CcgParse parse = parses.get(0);
    System.out.println(parse.getAllDependencies());
    System.out.println(parse.getSemanticHeads());

    Set<IndexedPredicate> expectedHeads = Sets.newHashSet(new IndexedPredicate("berries", 0));
    assertEquals(expectedHeads, parse.getSemanticHeads());
  }
  
  public void testBinaryRules2() {
    List<CcgParse> parses = parser.beamSearch(
        Arrays.asList("people", "eat", "berries", ";"), 10);

    assertEquals(1, parses.size());
    System.out.println(parses.get(0).getAllDependencies());

    CcgParse parse = parses.get(0);
    assertEquals(0.3 * 2 * 2 * 4, parse.getSubtreeProbability());
    assertEquals(2, parse.getAllDependencies().size());
  }

  public void testBinaryRules3() {
    List<CcgParse> parses = parser.beamSearch(
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
    List<CcgParse> parses = parser.beamSearch(
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
    List<CcgParse> parses = parser.beamSearch(
        Arrays.asList("or", "directed", "houses"), 10);
    assertEquals(1, parses.size());
    CcgParse parse = parses.get(0);
    assertEquals(SyntacticCategory.parseFrom("((S[b]\\N)\\(S[b]\\N))"), 
        parse.getSyntacticCategory());
  }

  public void testBinaryRulesConj3() {
    List<CcgParse> parses = parser.beamSearch(
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
        new DependencyStructure("eat", 1, "people", 0, 1),
        new DependencyStructure("eat", 1, "berries", 2, 2),
        new DependencyStructure("directed", 4, "people", 0, 2),
        new DependencyStructure("directed", 4, "houses", 5, 1));
    assertEquals(expectedDeps, deps);
    
    assertEquals(SyntacticCategory.parseFrom("S[b]"), parse.getSyntacticCategory());
  }
      
  public void testBinaryRulesNounCompound() {
    List<CcgParse> parses = parser.beamSearch(
        Arrays.asList("people", "berries"), 10);
    
    assertEquals(1, parses.size());
    CcgParse parse = parses.get(0);
    System.out.println(parse.getAllDependencies());
    assertEquals(2.0, parse.getSubtreeProbability());
    
    Set<DependencyStructure> observedDeps = Sets.newHashSet(parse.getAllDependencies());
    Set<DependencyStructure> expectedDeps = Sets.newHashSet(
        new DependencyStructure("special:compound", 1, "berries", 1, 2),
        new DependencyStructure("special:compound", 1, "people", 0, 1));
    assertEquals(expectedDeps, observedDeps);
  }

  public void testParseUnaryRules1() {
    List<CcgParse> parses = parserWithUnary.beamSearch(
        Arrays.asList("people", "eat", "berries", "or", "directed", "houses"), 10);

    for (CcgParse parse : parses) {
      System.out.println(parse);
    }
    
    assertEquals(2, parses.size());
    Set<DependencyStructure> expectedDeps = Sets.newHashSet(
        new DependencyStructure("eat", 1, "people", 0, 1),
        new DependencyStructure("eat", 1, "berries", 2, 2),
        new DependencyStructure("directed", 4, "people", 0, 2),
        new DependencyStructure("directed", 4, "houses", 5, 1));

    for (CcgParse parse : parses) {
      assertEquals(expectedDeps, Sets.newHashSet(parse.getAllDependencies()));
    }
  }
  
  public void testParseUnaryRules2() {
    List<CcgParse> parses = parserWithUnary.beamSearch(
        Arrays.asList("people", "eat", "people", "berries"), 10);
    
    assertEquals(4, parses.size());
    Set<DependencyStructure> expectedDeps = Sets.newHashSet(
        new DependencyStructure("eat", 1, "people", 0, 1),
        new DependencyStructure("eat", 1, "berries", 3, 2));
    
    for (CcgParse parse : parses) {
      Set<DependencyStructure> trueDeps = Sets.newHashSet(parse.getAllDependencies());
      assertTrue(trueDeps.containsAll(expectedDeps));
    }
  }
  
  public void testParseUnaryRules3() {
    List<CcgParse> parses = parserWithUnary.beamSearch(
        Arrays.asList("eat"), 10);
    
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

  public void testChartFilterApply() {
    ChartFilter filter = new TestChartFilter();
    List<CcgParse> parses = parserWithUnary.beamSearch(Arrays.asList("I", "eat", "berries", "in", "people", "houses"), 
        Collections.nCopies(6, ParametricCcgParser.DEFAULT_POS_TAG), 10, filter, new NullLogFunction());

    // The filter disallows the verb modifier syntactic category for "in" 
    SyntacticCategory expected = SyntacticCategory.parseFrom("(N\\N)/N");
    for (CcgParse parse : parses) {
      assertEquals(expected, parse.getLexiconEntryForWordIndex(3).getSyntax().getSyntax());
    }
  }
  
  public void testChartFilterApplyToTerminals() {
    ChartFilter filter = new TestChartFilter();
    List<CcgParse> parses = parserWithUnary.beamSearch(Arrays.asList("berries", "in", "people", "houses"), 
        Collections.nCopies(4, ParametricCcgParser.DEFAULT_POS_TAG), 10, filter, new NullLogFunction());
    
    for (CcgParse parse : parses) {
      assertNoNounCompound(parse);
    }
  }
  
  private void assertNoNounCompound(CcgParse parse) {
    SyntacticCategory noun = SyntacticCategory.parseFrom("N");
    
    if (!parse.isTerminal()) {
      assertFalse(parse.getLeft().getHeadedSyntacticCategory().getSyntax().equals(noun) &&
          parse.getRight().getHeadedSyntacticCategory().getSyntax().equals(noun) &&
          !parse.getLeft().hasUnaryRule() && !parse.getRight().hasUnaryRule());
      
      assertNoNounCompound(parse.getLeft());
      assertNoNounCompound(parse.getRight());
    }
  }
  
  public void testSerialization() throws IOException {
    ObjectOutputStream oos = new ObjectOutputStream(new NullOutputStream());
    oos.writeObject(parserWithUnary);
    oos.close();
  }

  private CcgParser parseLexicon(String[] lexicon, String[] binaryRuleArray, 
      String[] unaryRuleArray, double[] weights, boolean allowComposition) {
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

    // Build the terminal distribution.
    DiscreteVariable ccgCategoryType = new DiscreteVariable("ccgCategory", categories);
    DiscreteVariable wordType = new DiscreteVariable("words", words);
    DiscreteVariable posType = new DiscreteVariable("pos", 
        Lists.newArrayList(ParametricCcgParser.DEFAULT_POS_TAG));

    terminalVar = VariableNumMap.singleton(0, "words", wordType);
    ccgCategoryVar = VariableNumMap.singleton(1, "ccgCategory", ccgCategoryType);
    VariableNumMap vars = VariableNumMap.unionAll(terminalVar, ccgCategoryVar);
    TableFactorBuilder terminalBuilder = new TableFactorBuilder(vars, SparseTensorBuilder.getFactory());
    for (int i = 0; i < categories.size(); i++) {
      int commaInd = lexicon[i].indexOf(",");
      List<String> wordList = Arrays.asList(lexicon[i].substring(0, commaInd));
      CcgCategory category = CcgCategory.parseFrom(lexicon[i].substring(commaInd + 1));
      terminalBuilder.setWeight(vars.outcomeArrayToAssignment(wordList, category), weights[i]);
    }
    
    // Build the dependency distribution.
    DiscreteVariable semanticPredicateType = new DiscreteVariable("semanticPredicates", semanticPredicates);
    DiscreteVariable argumentNums = new DiscreteVariable("argNums", Ints.asList(0, 1, 2, 3));

    semanticHeadVar = VariableNumMap.singleton(0, "semanticHead", semanticPredicateType);
    semanticArgNumVar = VariableNumMap.singleton(1, "semanticArgNum", argumentNums);
    semanticArgVar = VariableNumMap.singleton(2, "semanticArg", semanticPredicateType);
    vars = VariableNumMap.unionAll(semanticHeadVar, semanticArgNumVar, semanticArgVar);
        
    TableFactorBuilder dependencyFactorBuilder = TableFactorBuilder.ones(vars);
    
    dependencyFactorBuilder.incrementWeight(vars.outcomeArrayToAssignment("eat", 2, "berries"), 1.0);
    dependencyFactorBuilder.incrementWeight(vars.outcomeArrayToAssignment("quickly", 1, "eat"), 3.0);
    dependencyFactorBuilder.incrementWeight(vars.outcomeArrayToAssignment("in", 1, "people"), 1.0);
    dependencyFactorBuilder.incrementWeight(vars.outcomeArrayToAssignment("special:compound", 1, "people"), 1.0);
    dependencyFactorBuilder.incrementWeight(vars.outcomeArrayToAssignment("green_(N{0}/N{0}){1}", 1, "people"), 1.0);
    
    DiscreteVariable syntaxType = CcgParser.buildSyntacticCategoryDictionary(syntacticCategories);
    DiscreteFactor syntaxDistribution = CcgParser.buildUnrestrictedBinaryDistribution(syntaxType, binaryRules, allowComposition);
    VariableNumMap leftSyntaxVar = syntaxDistribution.getVars().getVariablesByName(CcgParser.LEFT_SYNTAX_VAR_NAME);
    VariableNumMap rightSyntaxVar = syntaxDistribution.getVars().getVariablesByName(CcgParser.RIGHT_SYNTAX_VAR_NAME);
    VariableNumMap inputSyntaxVars = leftSyntaxVar.union(rightSyntaxVar); 
    VariableNumMap parentSyntaxVar = syntaxDistribution.getVars().getVariablesByName(CcgParser.PARENT_SYNTAX_VAR_NAME);
    
    //System.out.println(terminalBuilder.build().getParameterDescription());

    Preconditions.checkState(syntacticCombinations.length == syntacticCombinationWeights.length);
    for (int i = 0; i < syntacticCombinations.length; i++) {
      HeadedSyntacticCategory leftSyntax = HeadedSyntacticCategory.parseFrom(syntacticCombinations[i][0]).getCanonicalForm();
      HeadedSyntacticCategory rightSyntax = HeadedSyntacticCategory.parseFrom(syntacticCombinations[i][1]).getCanonicalForm();
      DiscreteFactor combinationFactor = TableFactor.pointDistribution(inputSyntaxVars, 
          inputSyntaxVars.outcomeArrayToAssignment(leftSyntax, rightSyntax)).product(syntacticCombinationWeights[i]);
      
      syntaxDistribution = syntaxDistribution.add(syntaxDistribution.product(combinationFactor));
    }
    
    // Distribution over unary rules.
    DiscreteFactor unaryRuleDistribution = CcgParser.buildUnaryRuleDistribution(unaryRules, 
        leftSyntaxVar.getDiscreteVariables().get(0));
    VariableNumMap unaryRuleInputVar = unaryRuleDistribution.getVars().getVariablesByName(CcgParser.UNARY_RULE_INPUT_VAR_NAME);
    VariableNumMap unaryRuleVar = unaryRuleDistribution.getVars().getVariablesByName(CcgParser.UNARY_RULE_VAR_NAME);
    
    DiscreteFactor compiledSyntaxDistribution = CcgParser.compileUnaryAndBinaryRules(unaryRuleDistribution, 
        syntaxDistribution, syntaxType);
    VariableNumMap searchMoveVar = compiledSyntaxDistribution.getVars().getVariablesByName(
        CcgParser.PARENT_MOVE_SYNTAX_VAR_NAME);
    
    // Distribution over the root of the tree.
    DiscreteFactor rootDistribution = TableFactor.unity(leftSyntaxVar);
    Assignment assignment = leftSyntaxVar.outcomeArrayToAssignment(HeadedSyntacticCategory.parseFrom("S[b]{0}"));
    rootDistribution = rootDistribution.add(TableFactor.pointDistribution(leftSyntaxVar, assignment));
    assignment = leftSyntaxVar.outcomeArrayToAssignment(HeadedSyntacticCategory.parseFrom("S[ng]{0}"));
    rootDistribution = rootDistribution.add(TableFactor.pointDistribution(leftSyntaxVar, assignment));

    // Distribution over pos tags and terminal syntactic types,
    // for smoothing sparse word counts.
    posTagVar = VariableNumMap.singleton(0, "posTag", posType);
    terminalSyntaxVar = VariableNumMap.singleton(1, "terminalSyntax", leftSyntaxVar.getDiscreteVariables().get(0));
    DiscreteFactor posDistribution = TableFactor.unity(posTagVar.union(terminalSyntaxVar));
    
    // Distribution over predicate-argument distances.
    VariableNumMap distancePredicateVars = semanticHeadVar.union(semanticArgNumVar);
    VariableNumMap wordDistanceVar = VariableNumMap.singleton(2, "wordDistance", CcgParser.wordDistanceVarType);
    VariableNumMap puncDistanceVar = VariableNumMap.singleton(2, "puncDistance", CcgParser.puncDistanceVarType);
    VariableNumMap verbDistanceVar = VariableNumMap.singleton(2, "verbDistance", CcgParser.verbDistanceVarType);
    DiscreteFactor wordDistanceFactor = TableFactor.unity(distancePredicateVars.union(wordDistanceVar));
    DiscreteFactor puncDistanceFactor = TableFactor.unity(distancePredicateVars.union(puncDistanceVar));
    DiscreteFactor verbDistanceFactor = TableFactor.unity(distancePredicateVars.union(verbDistanceVar));
    Set<String> puncTagSet = ParametricCcgParser.DEFAULT_PUNC_TAGS;
    Set<String> verbTagSet = ParametricCcgParser.DEFAULT_VERB_TAGS;
    
    VariableNumMap wordDistanceVars = wordDistanceFactor.getVars();
    TableFactorBuilder wordFactorBuilder = TableFactorBuilder.fromFactor(wordDistanceFactor);
    wordFactorBuilder.incrementWeight(wordDistanceVars.outcomeArrayToAssignment("eat", 2, 0), 3.0);
    wordFactorBuilder.incrementWeight(wordDistanceVars.outcomeArrayToAssignment("eat", 2, 1), 2.0);
    wordFactorBuilder.incrementWeight(wordDistanceVars.outcomeArrayToAssignment("eat", 2, 2), 1.0);
    wordDistanceFactor = wordFactorBuilder.build();

    return new CcgParser(terminalVar, ccgCategoryVar, terminalBuilder.build(),
        posTagVar, terminalSyntaxVar, posDistribution,
        semanticHeadVar, semanticArgNumVar, semanticArgVar, dependencyFactorBuilder.build(),
        wordDistanceVar, wordDistanceFactor, puncDistanceVar, puncDistanceFactor, puncTagSet, 
        verbDistanceVar, verbDistanceFactor, verbTagSet,
        leftSyntaxVar, rightSyntaxVar, parentSyntaxVar, syntaxDistribution, unaryRuleInputVar,
        unaryRuleVar, unaryRuleDistribution, searchMoveVar, compiledSyntaxDistribution,
        leftSyntaxVar, rootDistribution, false);
  }
  
  private static class TestChartFilter implements ChartFilter {

    @Override
    public boolean apply(ChartEntry entry, int spanStart, int spanEnd, DiscreteVariable syntaxVarType) {
      if (spanStart == 3 && spanEnd == 5) {
        HeadedSyntacticCategory syntax = (HeadedSyntacticCategory) syntaxVarType.getValue(entry.getHeadedSyntax());
        return syntax.getSyntax().equals(SyntacticCategory.parseFrom("N\\N"));
      }
      return true;
    }

    @Override
    public void applyToTerminals(CcgChart chart) {
      DiscreteFactor syntaxDistribution = chart.getSyntaxDistribution();
      VariableNumMap vars = syntaxDistribution.getVars();
      TableFactorBuilder builder = new TableFactorBuilder(vars, SparseTensorBuilder.getFactory());
      
      Iterator<Outcome> syntaxIter = syntaxDistribution.outcomeIterator();
      while (syntaxIter.hasNext()) {
        Outcome outcome = syntaxIter.next();
        CcgSearchMove move = (CcgSearchMove) outcome.getAssignment().getValues().get(2);
        
        CcgBinaryRule rule = move.getBinaryCombinator().getBinaryRule();
        if (rule != null) {
          String[] subjects = rule.getSubjects();
          for (int i =0; i < subjects.length; i++) {
            if (subjects[i].equals("special:compound")) {
              builder.setWeight(outcome.getAssignment(), -1.0);
            }
          }
        }
      }      
      DiscreteFactor syntaxDelta = builder.build();
      System.out.println(syntaxDelta.getParameterDescription());
     
      DiscreteFactor updatedSyntaxDistribution = syntaxDistribution.add(syntaxDelta);
      chart.setSyntaxDistribution(updatedSyntaxDistribution);
    }
  }
}