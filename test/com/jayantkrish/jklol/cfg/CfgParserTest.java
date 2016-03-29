package com.jayantkrish.jklol.cfg;

import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.util.Assignment;

public class CfgParserTest extends TestCase {

  DiscreteFactor root;
  DiscreteFactor binary;
  DiscreteFactor terminal;
	CfgParser p, p2;
	
	VariableNumMap parentVar, leftVar, rightVar, termVar, ruleVar;

	public void setUp() {
	  DiscreteVariable nonterm = new DiscreteVariable("nonterminals", Arrays.asList(
	      "N", "V", "S", "S2", "NP", "VP", "foo", "R", "bar", "barP", "A", "**skip**"));
	  DiscreteVariable terms = new DiscreteVariable("terminals", listifyWords(Arrays.asList(
	      "gretzky", "plays", "ice", "hockey", "ice hockey", "baz", "bbb", 
	      "baz bbb", "a", "b", "c")));
	  DiscreteVariable ruleTypes = new DiscreteVariable("rules", Arrays.asList("rule1", "rule2"));

	  leftVar = new VariableNumMap(Ints.asList(0), Arrays.asList("v0"), Arrays.asList(nonterm));
	  rightVar = new VariableNumMap(Ints.asList(1), Arrays.asList("v1"), Arrays.asList(nonterm));
	  termVar = new VariableNumMap(Ints.asList(2), Arrays.asList("v2"), Arrays.asList(terms));
	  parentVar = new VariableNumMap(Ints.asList(3), Arrays.asList("v3"), Arrays.asList(nonterm));	  
	  ruleVar = new VariableNumMap(Ints.asList(4), Arrays.asList("v4"), Arrays.asList(ruleTypes));
	  
	  // TableFactorBuilder rootBuilder = new TableFactorBuilder(parentVar, DenseTensorBuilder.getFactory());
	  VariableNumMap binaryFactorVars = VariableNumMap.unionAll(parentVar, leftVar, rightVar, ruleVar);
	  TableFactorBuilder binaryBuilder = new TableFactorBuilder(binaryFactorVars, SparseTensorBuilder.getFactory());
	  VariableNumMap terminalFactorVars = VariableNumMap.unionAll(parentVar, termVar, ruleVar);
	  TableFactorBuilder terminalBuilder = new TableFactorBuilder(terminalFactorVars, SparseTensorBuilder.getFactory());
	    
		addTerminal(terminalBuilder, "N", "gretzky", "rule1", 0.25);
		addTerminal(terminalBuilder, "N", "ice hockey", "rule1", 0.25);
		addTerminal(terminalBuilder, "N", "ice", "rule1", 0.25);
		addTerminal(terminalBuilder, "N", "hockey", "rule1", 0.25);
		addTerminal(terminalBuilder, "V", "plays", "rule1", 1.0);

		addBinary(binaryBuilder, "S", "N", "VP", "rule1", 1.0);
		addBinary(binaryBuilder, "S2", "N", "VP", "rule1", 1.0);
		addBinary(binaryBuilder, "VP", "V", "N", "rule2", 1.0);
		addBinary(binaryBuilder, "NP", "N", "N", "rule1", 1.0);
		addBinary(binaryBuilder, "foo", "N", "N", "rule1", 0.5);
		addBinary(binaryBuilder, "foo", "R", "S", "rule1", 0.5);

		addTerminal(terminalBuilder, "bar", "baz", "rule1", 0.5);
		addTerminal(terminalBuilder, "bar", "bbb", "rule1", 0.5);
		addTerminal(terminalBuilder, "barP", "baz bbb", "rule1", 0.5);
		addBinary(binaryBuilder, "barP", "bar", "bar", "rule1", 0.5);
		addBinary(binaryBuilder, "barP", "bar", "bar", "rule2", 0.25);

		addBinary(binaryBuilder, "A", "A", "A", "rule1", 0.25);
		addTerminal(terminalBuilder, "A", "a", "rule1", 0.25);
		addTerminal(terminalBuilder, "A", "b", "rule1", 0.25);
		addTerminal(terminalBuilder, "A", "c", "rule1", 0.25);

		root = TableFactor.unity(parentVar).add(
		    TableFactor.pointDistribution(parentVar, parentVar.outcomeArrayToAssignment("S2")).product(-0.5));
		binary = binaryBuilder.build();
		terminal = terminalBuilder.build();
		p = new CfgParser(parentVar, leftVar, rightVar, termVar, ruleVar, 
		    root, binary, terminal, false, null);

		Assignment skipAssignment = parentVar.outcomeArrayToAssignment("**skip**")
		    .union(ruleVar.outcomeArrayToAssignment("rule1"));
		for (Object terminal : terms.getValues()) {
		  Assignment a = termVar.outcomeArrayToAssignment(terminal).union(skipAssignment);
		  terminalBuilder.setWeight(a, 1.0);
		}
		
		p2 = new CfgParser(parentVar, leftVar, rightVar, termVar, ruleVar, 
		    root, binaryBuilder.build(), terminalBuilder.build(), true, skipAssignment);
	}
	
	private void addTerminal(TableFactorBuilder terminalBuilder, String nonterm, 
	    String term, String ruleType, double weight) {
	  List<String> terminalValue = Arrays.asList(term.split(" "));
	  terminalBuilder.incrementWeight(terminalBuilder.getVars()
	      .outcomeArrayToAssignment(terminalValue, nonterm, ruleType), weight);
	}
	
	private void addBinary(TableFactorBuilder binaryBuilder, String parent, String left, 
	    String right, String ruleType, double weight) {
	  binaryBuilder.incrementWeight(binaryBuilder.getVars()
	      .outcomeArrayToAssignment(left, right, parent, ruleType), weight);
	}
	
	/**
	 * Splits each string in the inputVar into component words and adds the result
	 * to the returned array.
	 * @return
	 */
	private List<List<String>> listifyWords(List<String> strings) {
	  List<List<String>> wordSequences = Lists.newArrayList();
	  for (String string : strings) {
	    wordSequences.add(Arrays.asList(string.split(" ")));
	  }
	  return wordSequences;
	}

	public void testParseInsideMarginal() {
		CfgParseChart c = p.parseInsideMarginal(Arrays.asList("gretzky", "plays", "ice", "hockey"), true);
		
		Factor rootProductions = c.getInsideEntries(0, 3);
		assertEquals(2, rootProductions.coerceToDiscrete().getNonzeroAssignments().size());
		assertEquals(0.25 * .25, rootProductions.getUnnormalizedProbability("S"));
		assertEquals(0.25 * .25, rootProductions.getUnnormalizedProbability("S2"));

		Factor nounProductions = c.getInsideEntries(2, 3);
		assertEquals(3, nounProductions.coerceToDiscrete().getNonzeroAssignments().size());
		assertEquals(.25, nounProductions.getUnnormalizedProbability("N"));
		assertEquals(.25 * .25, nounProductions.getUnnormalizedProbability("NP"));
		assertEquals(.5 * .25 * .25, nounProductions.getUnnormalizedProbability("foo"));
	}

	public void testParseOutsideMarginal() {
		CfgParseChart c = p.parseMarginal(Arrays.asList("gretzky", "plays", "ice", "hockey"), "S", true);

		Factor rootProductions = c.getOutsideEntries(0, 3);
		assertEquals(1, rootProductions.coerceToDiscrete().getNonzeroAssignments().size());
		assertEquals(1.0, rootProductions.getUnnormalizedProbability("S"));

		Factor vpProductions = c.getOutsideEntries(1, 3);
		assertEquals(.25, vpProductions.getUnnormalizedProbability("VP"));
	}

	public void testParseMarginal() {
	  CfgParseChart c = p.parseMarginal(Arrays.asList("gretzky", "plays", "ice", "hockey"), "S", true);

		Factor rootProductions = c.getMarginalEntries(0, 3);
		assertEquals(1, rootProductions.coerceToDiscrete().getNonzeroAssignments().size());
		assertEquals(1.0, rootProductions.getUnnormalizedProbability("S") / c.getPartitionFunction());

		Factor nProductions = c.getMarginalEntries(2, 3);
		assertEquals(1.0, nProductions.getUnnormalizedProbability("N") / c.getPartitionFunction());
	}

	public void testParseMarginalRootProbs() {
	  CfgParseChart c = p.parseMarginal(Arrays.asList("gretzky", "plays", "ice", "hockey"), true);

	  Factor rootProductions = c.getMarginalEntries(0, 3);
	  double partitionFunction = rootProductions.getTotalUnnormalizedProbability();
	  assertEquals(2, rootProductions.coerceToDiscrete().getNonzeroAssignments().size());
	  assertEquals(2.0 / 3, rootProductions.getUnnormalizedProbability("S") / partitionFunction, 0.001);
	  assertEquals(1.0 / 3, rootProductions.getUnnormalizedProbability("S2") / partitionFunction, 0.001);
	}

	public void testParseMarginalWordSkip() {
	  CfgParseChart c = p2.parseMarginal(Arrays.asList("plays", "hockey"), true);

		Factor rootProductions = c.getMarginalEntries(0, 1);
		assertEquals(3, rootProductions.coerceToDiscrete().getNonzeroAssignments().size());
		assertEquals(1.0 / 6.0, rootProductions.getUnnormalizedProbability("N") / c.getPartitionFunction());
		assertEquals(4.0 / 6.0, rootProductions.getUnnormalizedProbability("V") / c.getPartitionFunction());
		assertEquals(1.0 / 6.0, rootProductions.getUnnormalizedProbability("VP") / c.getPartitionFunction());

		Factor terminalExpectations = c.getTerminalRuleExpectations();
		assertEquals(4, terminalExpectations.coerceToDiscrete().getNonzeroAssignments().size());
		assertEquals(2.0 / 6.0, terminalExpectations.getUnnormalizedProbability(Arrays.asList("hockey"), "N", "rule1") / c.getPartitionFunction());
		assertEquals(4.0 / 6.0, terminalExpectations.getUnnormalizedProbability(Arrays.asList("hockey"), "**skip**", "rule1") / c.getPartitionFunction());
		assertEquals(5.0 / 6.0, terminalExpectations.getUnnormalizedProbability(Arrays.asList("plays"), "V", "rule1") / c.getPartitionFunction());
		assertEquals(1.0 / 6.0, terminalExpectations.getUnnormalizedProbability(Arrays.asList("plays"), "**skip**", "rule1") / c.getPartitionFunction());

		c = p2.parseMarginal(Arrays.asList("plays", "hockey"), false);
		CfgParseTree bestParse = c.getBestParseTree();
		assertTrue(bestParse.isTerminal());
		assertEquals("V", bestParse.getRoot());
		assertEquals(Arrays.asList("plays"), bestParse.getTerminalProductions());
	}

	public void testRuleCounts() {
	  CfgParseChart c = p.parseMarginal(Arrays.asList("gretzky", "plays", "ice", "hockey"), "S", true);

		Factor ruleCounts = c.getBinaryRuleExpectations();
		// System.out.println(ruleCounts.getParameterDescription());
		assertEquals(0.0, ruleCounts.getUnnormalizedProbability("N", "VP", "S2", "rule1"));
		assertEquals(1.0, ruleCounts.getUnnormalizedProbability("N", "VP", "S", "rule1") / c.getPartitionFunction());
		assertEquals(0.0, ruleCounts.getUnnormalizedProbability("N", "N", "NP", "rule1"));
		assertEquals(1.0, ruleCounts.getUnnormalizedProbability("V", "N", "VP", "rule2") / c.getPartitionFunction());
		assertEquals(0.0, ruleCounts.getUnnormalizedProbability("V", "N", "VP", "rule1") / c.getPartitionFunction());

		Factor termCounts = c.getTerminalRuleExpectations();
		assertEquals(1.0, termCounts.getUnnormalizedProbability(Arrays.asList("gretzky"), "N", "rule1") / c.getPartitionFunction());
		assertEquals(0.0, termCounts.getUnnormalizedProbability(Arrays.asList("hockey"), "N", "rule1"));
	}

	public void testAmbiguous() {
	  CfgParseChart c = p.parseMarginal(Arrays.asList("a", "b", "c"), "A", true);

		Factor leftProds = c.getMarginalEntries(0, 1);
		assertEquals(0.5, leftProds.getUnnormalizedProbability("A") / c.getPartitionFunction());

		leftProds = c.getMarginalEntries(0, 0);
		assertEquals(1.0, leftProds.getUnnormalizedProbability("A") / c.getPartitionFunction());

		Factor ruleCounts = c.getBinaryRuleExpectations();
		assertEquals(2.0, ruleCounts.getUnnormalizedProbability("A", "A", "A", "rule1") / c.getPartitionFunction());

		Factor termCounts = c.getTerminalRuleExpectations();
		assertEquals(1.0, termCounts.getUnnormalizedProbability(Arrays.asList("b"), "A", "rule1") / c.getPartitionFunction());	
	}

	public void testParseMaxMarginal() {
		CfgParseChart c = p.parseMarginal(Arrays.asList("baz", "bbb"), "barP", false);
		Factor prods = c.getInsideEntries(0, 1);
		assertEquals(1, prods.coerceToDiscrete().getNonzeroAssignments().size());
		assertEquals(.5, prods.getUnnormalizedProbability("barP"));	
	}

	public void testParseMaxMarginalTree() {
	  CfgParseChart c = p.parseMarginal(Arrays.asList("gretzky", "plays", "ice", "hockey"), false); 

		CfgParseTree t = c.getBestParseTree("S");
		assertEquals("S", t.getRoot());
		assertEquals("N", t.getLeft().getRoot());
		assertEquals("VP", t.getRight().getRoot());
		assertEquals("V", t.getRight().getLeft().getRoot());
	}

	public void testBeamSearch() {
	  List<CfgParseTree> trees = p.beamSearch(Arrays.asList("baz", "bbb"), 10);
	  assertEquals(3, trees.size());
	  
	  CfgParseTree bestTree = trees.get(0);
	  assertEquals("barP", bestTree.getRoot());
	  assertEquals("rule1", bestTree.getRuleType());
	  assertTrue(bestTree.isTerminal());
	  assertEquals(0.5, bestTree.getProbability());
	  
	  CfgParseTree secondBestTree = trees.get(1);
	  assertEquals("barP", secondBestTree.getRoot());
	  assertEquals("rule1", secondBestTree.getRuleType());
	  assertFalse(secondBestTree.isTerminal());
	  assertEquals(0.125, secondBestTree.getProbability());
	  
	  CfgParseTree thirdBestTree = trees.get(2);
	  assertEquals("barP", thirdBestTree.getRoot());
	  assertEquals("rule2", thirdBestTree.getRuleType());
	  assertFalse(thirdBestTree.isTerminal());
	  assertEquals(0.125 / 2.0, thirdBestTree.getProbability());

	  
	  // Make sure that the beam truncates the less probable tree.
	  trees = p.beamSearch(Arrays.asList("baz", "bbb"), 1);
	  assertEquals(1, trees.size());
	  bestTree = trees.get(0);
	  assertEquals("barP", bestTree.getRoot());
	  assertTrue(bestTree.isTerminal());
	  assertEquals(0.5, bestTree.getProbability());
	}
	
	public void testBeamSearch2() {
	  List<CfgParseTree> trees = p.beamSearch(Arrays.asList("a", "a", "a", "a"), 10);
	  assertEquals(5, trees.size());
	  
	  for (CfgParseTree tree : trees) {
	    assertEquals("A", tree.getRoot());
	    assertEquals("rule1", tree.getRuleType());
	    assertFalse(tree.isTerminal());
	    assertEquals(1.0 / Math.pow(4, 7), tree.getProbability());
	  }
	}
}
