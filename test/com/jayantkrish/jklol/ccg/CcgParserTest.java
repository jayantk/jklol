package com.jayantkrish.jklol.ccg;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;

public class CcgParserTest extends TestCase {

  CcgParser parser, parserWithComposition, parserWithUnary;
  
  private static final String[] lexicon = {"I,N{0},0 I", "people,N{0},0 people", "berries,N{0},0 berries", "houses,N{0},0 houses",
    "eat,((S{0}\\N{1}){0}/N{2}){0},0 eat,eat 1 1,eat 2 2", "that,((N{1}\\N{1}){0}/(S{2}\\N{1}){2}){0},0 that,that 1 1,that 2 2", 
    "quickly,(((S{1}\\N{2}){1}/N{3}){1}/((S{1}\\N{2}){1}/N{3}){1}){0},0 quickly,quickly 1 1", 
    "in,((N{1}\\N{1}){0}/N{2}){0},0 in,in 1 1,in 2 2",
    "amazingly,((N{1}/N{1}){2}/(N{1}/N{1}){2}){0},0 amazingly,amazingly 1 2",
    "tasty,(N{1}/N{1}){0},0 tasty,tasty 1 1",
    "in,(((S{1}\\N{2}){1}\\(S{1}\\N{2}){1}){0}/N{3}){0},0 in,in 1 1,in 2 3",
    "and,((N{1}\\N{1}){0}/N{1}){0},0 and", 
    "almost,(((N{1}\\N{1}){2}/N{3}){2}/((N{1}\\N{1}){2}/N{3}){2}){0},0 almost,almost 1 2",
    "is,((S{0}\\N{1}){0}/N{2}){0},0 is,is 1 1, is 2 2", 
    "directed,((S{0}\\N{1}){0}/N{2}){0},0 directed,directed 1 2,directed 2 1",
    ";,;{0},0 ;", "or,conj{0},0 or",
    "about,(NP{0}/(S{1}\\N{2}){1}){0},0 about,about 1 1", 
    "eating,((S{0}\\N{1}){0}/N{2}){0},0 eat,eat 1 1,eat 2 2",
    "rapidly,((S{1}\\N{2}){1}/(S{1}\\N{2}){1}){0},0 rapidly,rapidly 1 1",
    "colorful,(N{1}/N{1}){0},0 colorful,colorful 1 1",
    "*NOT_A_WORD*,(NP{0}/N{1}){0},0 *NOT_A_WORD*"};
  
  private static final double[] weights = {0.5, 1.0, 1.0, 1.0, 
    0.3, 1.0, 
    1.0, 1.0,
    1.0, 1.0,
    0.5, 1.0, 2.0,
    0.25, 1.0,
    1.0, 0.5,
    1.0, 1.0,
    0.5, 1.0,
    1.0};

  private static final String[] binaryRuleArray = {";{1} N{0} N{0}", "N{0} ;{1} N{0}", 
    ";{2} (S{0}\\N{1}){0} (N{0}\\N{1}){0}", "\",{2} N{0} (N{0}\\N{0}){1}\"", "conj{1} N{0} (N{0}\\N{0}){1}",  
    "conj{2} (S{0}\\N{1}){0} ((S{0}\\N{1}){0}\\(S{0}\\N{1}){0}){2}",
    "\"N{0} N{1} N{1}\",\"special:compound 1 0\",\"special:compound 2 1\""};
  
  private static final String[] unaryRuleArray = {"N{0} (S{1}/(S{1}\\N{0}){1}){1}",
    "N{0} (N{1}/N{1}){0}"};
  
  private VariableNumMap terminalVar;
  private VariableNumMap ccgCategoryVar;
  
  private VariableNumMap semanticHeadVar;
  private VariableNumMap semanticArgNumVar;
  private VariableNumMap semanticArgVar;

  public void setUp() {
    parser = parseLexicon(lexicon, binaryRuleArray, new String[0], weights, false);
    parserWithComposition = parseLexicon(lexicon, binaryRuleArray, new String[0], weights, true);
    parserWithUnary = parseLexicon(lexicon, binaryRuleArray, unaryRuleArray, weights, false);
  }
  
  public void testParse() {
    List<CcgParse> parses = parser.beamSearch(
        Arrays.asList("I", "quickly", "eat", "amazingly", "tasty", "berries"), 20);
    assertEquals(1, parses.size());
    CcgParse parse = parses.get(0);

    assertEquals(1.0, parse.getNodeProbability());
    assertEquals(2.0, parse.getRight().getNodeProbability());
    System.out.println(parse.getRight().getLeft());
    assertEquals(4.0, parse.getRight().getLeft().getNodeProbability());
    assertEquals(0.5, parse.getLeft().getNodeProbability());
    assertEquals(0.5 * 0.3 * 2.0 * 4.0, parse.getSubtreeProbability());
    
    assertEquals(5, parse.getAllDependencies().size());
    
    assertEquals("eat", parse.getNodeDependencies().get(0).getHead());
    assertEquals(1, parse.getNodeDependencies().get(0).getArgIndex());
    assertEquals("I", parse.getNodeDependencies().get(0).getObject());
    assertEquals("quickly", parse.getRight().getLeft().getAllDependencies().get(0).getHead());
    assertEquals(1, parse.getRight().getLeft().getAllDependencies().get(0).getArgIndex());
    assertEquals("eat", parse.getRight().getLeft().getAllDependencies().get(0).getObject());
    
    assertEquals("eat", Iterables.getOnlyElement(parse.getSemanticHeads()).getHead());
    assertEquals("I", Iterables.getOnlyElement(parse.getLeft().getSemanticHeads()).getHead());
    
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
    assertEquals(0.3 * 4 * 2 * 2, parse.getSubtreeProbability());
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

    assertEquals(0.3 * 4 * 2, parse.getSubtreeProbability());
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
    
    SyntacticCategory expectedSyntax = SyntacticCategory.parseFrom("(S\\N)/N");
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
    
    SyntacticCategory expectedSyntax = SyntacticCategory.parseFrom("(S\\N)/N");
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
  }
  
  public void testParseComposition4() {
    List<CcgParse> parses = parserWithComposition.beamSearch(
        Arrays.asList("I", "quickly", "eat", "amazingly", "tasty", "berries"), 20);
    assertEquals(3, parses.size());
    
    Set<DependencyStructure> expectedDeps = Sets.newHashSet(
        new DependencyStructure("eat", 2, "I", 0, 1),
        new DependencyStructure("eat", 2, "berries", 5, 2),
        new DependencyStructure("quickly", 1, "eat", 2, 1),
        new DependencyStructure("amazingly", 3, "tasty", 4, 1),
        new DependencyStructure("tasty", 4, "berries", 5, 1));

    for (CcgParse parse : parses) {
      assertEquals(expectedDeps, Sets.newHashSet(parse.getAllDependencies()));
    }
  }

  public void testParseHeadUnification() {
    List<CcgParse> parses = parser.beamSearch(10, 
        "people", "and", "houses", "eat", "berries", "and", "berries");
    
    assertEquals(1, parses.size());

    // Both parses should have the same probability and dependencies.
    CcgParse parse = parses.get(0);
    System.out.println(parse.getAllDependencies());
    assertEquals(0.3 * 2 * 2, parse.getSubtreeProbability());
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
    assertEquals(0.3 * 2, parse.getSubtreeProbability());
    assertEquals(2, parse.getAllDependencies().size());
  }

  public void testBinaryRules3() {
    List<CcgParse> parses = parser.beamSearch(
        Arrays.asList("people", ";", "eat", "berries", ";"), 10);
    
    assertEquals(3, parses.size());

    Set<String> syntaxTypes = Sets.newHashSet();
    for (CcgParse parse : parses) {
      assertEquals(0.3 * 2, parse.getSubtreeProbability());
      assertEquals(2, parse.getAllDependencies().size());
      syntaxTypes.add(parse.getSyntacticCategory().getValue());
    }
    Set<String> expectedTypes = Sets.newHashSet("N", "S");
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
        Arrays.asList("people", "eat", "berries", "or", "directed", "houses"), 10);
    
    assertEquals(1, parses.size());
    CcgParse parse = parses.get(0);
    
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

    terminalVar = VariableNumMap.singleton(0, "words", wordType);
    ccgCategoryVar = VariableNumMap.singleton(1, "ccgCategory", ccgCategoryType);
    VariableNumMap vars = terminalVar.union(ccgCategoryVar);
    TableFactorBuilder terminalBuilder = new TableFactorBuilder(vars, SparseTensorBuilder.getFactory());
    for (int i = 0; i < categories.size(); i++) {
      int commaInd = lexicon[i].indexOf(",");
      List<String> wordList = Arrays.asList(lexicon[i].substring(0, commaInd));
      CcgCategory category = CcgCategory.parseFrom(lexicon[i].substring(commaInd + 1));
      terminalBuilder.setWeight(vars.outcomeArrayToAssignment(wordList, category), weights[i]);
    }

    // Build the dependency distribution.
    DiscreteVariable semanticPredicateType = new DiscreteVariable("semanticPredicates", semanticPredicates);
    DiscreteVariable argumentNums = new DiscreteVariable("argNums", Ints.asList(1, 2, 3));

    semanticHeadVar = VariableNumMap.singleton(0, "semanticHead", semanticPredicateType);
    semanticArgNumVar = VariableNumMap.singleton(1, "semanticArgNum", argumentNums);
    semanticArgVar = VariableNumMap.singleton(2, "semanticArg", semanticPredicateType);
    vars = VariableNumMap.unionAll(semanticHeadVar, semanticArgNumVar, semanticArgVar);
        
    TableFactorBuilder dependencyFactorBuilder = TableFactorBuilder.ones(vars);
    
    dependencyFactorBuilder.incrementWeight(vars.outcomeArrayToAssignment("eat", 2, "berries"), 1.0);
    dependencyFactorBuilder.incrementWeight(vars.outcomeArrayToAssignment("quickly", 1, "eat"), 3.0);
    dependencyFactorBuilder.incrementWeight(vars.outcomeArrayToAssignment("in", 1, "people"), 1.0);
    dependencyFactorBuilder.incrementWeight(vars.outcomeArrayToAssignment("special:compound", 1, "people"), 1.0);
    
    DiscreteFactor syntaxDistribution = CcgParser.buildSyntacticDistribution(syntacticCategories,
        binaryRules, allowComposition);
    VariableNumMap leftSyntaxVar = syntaxDistribution.getVars().getVariablesByName(CcgParser.LEFT_SYNTAX_VAR_NAME);
    VariableNumMap rightSyntaxVar = syntaxDistribution.getVars().getVariablesByName(CcgParser.RIGHT_SYNTAX_VAR_NAME);
    VariableNumMap parentSyntaxVar = syntaxDistribution.getVars().getVariablesByName(CcgParser.PARENT_SYNTAX_VAR_NAME);
    
    return new CcgParser(terminalVar, ccgCategoryVar, terminalBuilder.build(),
        semanticHeadVar, semanticArgNumVar, semanticArgVar, dependencyFactorBuilder.build(),
        leftSyntaxVar, rightSyntaxVar, parentSyntaxVar, syntaxDistribution, unaryRules);
  }
}