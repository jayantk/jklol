package com.jayantkrish.jklol.ccg;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;

public class CcgParserTest extends TestCase {

  CcgParser parser;
  
  private static final String[] lexicon = {"I,N,", "people,N,", "berries,N", "houses,N", 
    "eat,(S\\N)/N,eat 1 ?1#eat 2 ?2", "that,(N\\>N)/(S\\N),that 1 ?1#?2 1 ?1#that 2 ?2", 
    "quickly,((S\\N)/N)/>((S\\N)/N),quickly 1 ?3#?3 1 ?1#?3 2 ?2", "in,(N\\>N)/N,in 1 ?1#in 2 ?2",
    "amazingly,(N/>N)/>(N/>N),amazingly 1 ?2#?2 1 ?1", "tasty,(N/>N),tasty 1 ?1",
    "in,((S\\N)\\>(S\\N))/N,in 1 ?2#in 2 ?3#?2 3 ?3#?2 1 ?1"};
  private static final double[] weights = {0.5, 1.0, 1.0, 1.0, 
    0.3, 1.0, 
    1.0, 1.0,
    1.0, 1.0,
    0.5};
  
  private VariableNumMap terminalVar;
  private VariableNumMap ccgCategoryVar;
  
  private VariableNumMap semanticHeadVar;
  private VariableNumMap semanticArgNumVar;
  private VariableNumMap semanticArgVar;

  public void setUp() {
    parser = parseLexicon(lexicon, weights);
  }
  
  public void testParse() {
    List<CcgParse> parses = parser.beamSearch(Arrays.asList("I", "quickly", "eat", "amazingly", "tasty", "berries"));
    
    assertEquals(1, parses.size());
    CcgParse parse = parses.get(0);
    
    System.out.println(parses.get(0));
    System.out.println(parses.get(0).getAllDependencies());
    
    assertEquals(0.5 * 0.3 * 2.0 * 4.0, parse.getSubtreeProbability());
    assertEquals(1.0, parse.getNodeProbability());
    assertEquals(2.0, parse.getRight().getNodeProbability());
    assertEquals(4.0, parse.getRight().getLeft().getNodeProbability());
    assertEquals(0.5, parse.getLeft().getNodeProbability());
    
    assertEquals(5, parse.getAllDependencies().size());
    
    assertEquals("eat", parse.getNodeDependencies().get(0).getHead());
    assertEquals(1, parse.getNodeDependencies().get(0).getArgIndex());
    assertEquals("I", parse.getNodeDependencies().get(0).getObject());
    assertEquals("quickly", parse.getRight().getLeft().getAllDependencies().get(0).getHead());
    assertEquals(1, parse.getRight().getLeft().getAllDependencies().get(0).getArgIndex());
    assertEquals("eat", parse.getRight().getLeft().getAllDependencies().get(0).getObject());

  }
  
  public void testParse2() {
    List<CcgParse> parses = parser.beamSearch(Arrays.asList("people", "that", "quickly", "eat", "berries", "in", "houses"));
    
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

    parse = parses.get(1);
    assertEquals(2, parse.getNodeDependencies().size());
    // Parse should have "people" as an arg1 dependency for both "that" and "eat"
    assertEquals(1, parse.getNodeDependencies().get(0).getArgIndex());
    assertEquals("people", parse.getNodeDependencies().get(0).getObject());
    assertEquals(1, parse.getNodeDependencies().get(1).getArgIndex());
    assertEquals("people", parse.getNodeDependencies().get(1).getObject());

    Set<String> heads = Sets.newHashSet(parse.getNodeDependencies().get(0).getHead(), 
        parse.getNodeDependencies().get(1).getHead());
    assertTrue(heads.contains("that"));
    assertTrue(heads.contains("eat"));

    assertEquals(0.3 * 4 * 2, parse.getSubtreeProbability());
  }

  
  private CcgParser parseLexicon(String[] lexicon, double[] weights) {
    Preconditions.checkArgument(lexicon.length == weights.length);
    List<CcgCategory> categories = Lists.newArrayList();
    Set<List<String>> words = Sets.newHashSet();
    Set<String> semanticPredicates = Sets.newHashSet();
    for (int i = 0; i < lexicon.length; i++) {
      CcgCategory category = CcgCategory.parseFrom(lexicon[i]);
      categories.add(category);
      for (String head : category.getHeads()) {
        words.add(Lists.newArrayList(head.split(" ")));
        semanticPredicates.addAll(Arrays.asList(head.split(" ")));
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
      for (String head : categories.get(i).getHeads()) {
        terminalBuilder.setWeight(vars.outcomeArrayToAssignment(Arrays.asList(head.split(" ")), 
            categories.get(i)), weights[i]);
      }
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
    
    return new CcgParser(10, terminalVar, ccgCategoryVar, terminalBuilder.build(),
        semanticHeadVar, semanticArgNumVar, semanticArgVar, dependencyFactorBuilder.build());
  }
}