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
import com.jayantkrish.jklol.ccg.CcgCategory.Argument;
import com.jayantkrish.jklol.ccg.CcgChart.IndexedPredicate;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;

public class CcgParserTest extends TestCase {

  CcgParser parser;
  
  private static final String[] lexicon = {"I,I,N", "people,people,N,", "berries,berries,N", "houses,houses,N",
    "eat,eat,(S\\\\N)/N,eat 1 ?1#eat 2 ?2", "that,that,(N\\\\>N)/(S\\\\N),that 1 ?1#?2 1 ?1#that 2 ?2", 
    "quickly,quickly,((S\\\\N)/N)/>((S\\\\N)/N),quickly 1 ?3", "in,in,(N\\\\>N)/N,in 1 ?1#in 2 ?2",
    "amazingly,amazingly,(N/>N)/>(N/>N),amazingly 1 ?2", "tasty,tasty,(N/>N),tasty 1 ?1",
    "in,in,((S\\\\N)\\\\>(S\\\\N))/N,in 1 ?2#in 2 ?3",
    "and,?1#?2,(N\\\\N)/N", "almost,almost,((N\\\\>N)/N)/>((N\\\\>N)/N),almost 1 ?3",
    "is,is,(S\\\\N)/N,is 1 ?1, is 2 ?2", "directed,directed,(S\\\\N)/N,directed 1 ?2#directed 2 ?1",
    ";,;,;", "or,or,conj,"};
  
  private static final double[] weights = {0.5, 1.0, 1.0, 1.0, 
    0.3, 1.0, 
    1.0, 1.0,
    1.0, 1.0,
    0.5, 1.0, 2.0,
    0.25, 1.0, 
    1.0, 0.5};
  
  private static final String[] rules = {"\"; N N\",\"F\",\"T\"", "\"N ; N\",\"T\",\"F\"", 
    "\"; S\\N N\\N\",\"F\",\"T\"", "\"conj N N\\N\",\"F\",\"T\",\"?1\"", 
    "\"conj S\\N (S\\N)\\(S\\N)\",\"F\",\"T\",\"?2\",\"?2 1 ?1\""};
  
  private VariableNumMap terminalVar;
  private VariableNumMap ccgCategoryVar;
  
  private VariableNumMap semanticHeadVar;
  private VariableNumMap semanticArgNumVar;
  private VariableNumMap semanticArgVar;

  public void setUp() {
    parser = parseLexicon(lexicon, rules, weights);
  }
  
  public void testParse() {
    List<CcgParse> parses = parser.beamSearch(
        Arrays.asList("I", "quickly", "eat", "amazingly", "tasty", "berries"), 10);
    
    assertEquals(1, parses.size());
    CcgParse parse = parses.get(0);
    
    System.out.println(parses.get(0));
    System.out.println(parses.get(0).getAllDependencies());
    
    assertEquals(1.0, parse.getNodeProbability());
    assertEquals(2.0, parse.getRight().getNodeProbability());
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
  }
  
  public void testParse2() {
    List<CcgParse> parses = parser.beamSearch(
        Arrays.asList("people", "that", "quickly", "eat", "berries", "in", "houses"), 10);
    
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

  public void testParseHeadUnification() {
    List<CcgParse> parses = parser.beamSearch(
        Arrays.asList("people", "and", "houses", "eat", "berries", "and", "berries"), 10);
    
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
  
  public void testBinaryRules() {
    List<CcgParse> parses = parser.beamSearch(
        Arrays.asList("people", "eat", "berries", ";"), 10);
    
    assertEquals(1, parses.size());
    
    CcgParse parse = parses.get(0);
    assertEquals(0.3 * 2, parse.getSubtreeProbability());
    assertEquals(2, parse.getAllDependencies().size());
  }
  
   public void testBinaryRules2() {
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
  
  private CcgParser parseLexicon(String[] lexicon, String[] rules, double[] weights) {
    Preconditions.checkArgument(lexicon.length == weights.length);
    List<CcgCategory> categories = Lists.newArrayList();
    Set<List<String>> words = Sets.newHashSet();
    Set<String> semanticPredicates = Sets.newHashSet();
    for (int i = 0; i < lexicon.length; i++) {
      int commaInd = lexicon[i].indexOf(",");
      words.add(Arrays.asList(lexicon[i].substring(0, commaInd)));

      CcgCategory category = CcgCategory.parseFrom(lexicon[i].substring(commaInd + 1));
      categories.add(category);
      for (Argument head : category.getHeads()) {
        if (head.hasPredicate()) {
          semanticPredicates.addAll(Arrays.asList(head.getPredicate()));
        }
      }
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
    
    // Parse the binary rules
    List<CcgBinaryRule> binaryRules = Lists.newArrayList();
    for (int i = 0; i < rules.length; i++) {
      binaryRules.add(CcgBinaryRule.parseFrom(rules[i]));
    }
    
    return new CcgParser(terminalVar, ccgCategoryVar, terminalBuilder.build(),
        semanticHeadVar, semanticArgNumVar, semanticArgVar, dependencyFactorBuilder.build(),
        binaryRules);
  }
}