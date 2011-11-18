package com.jayantkrish.jklol.cfg;

import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;

public class CfgParserTest extends TestCase {

  Factor binary;
  Factor terminal;
	CfgParser p;
	
	VariableNumMap parentVar, leftVar, rightVar, termVar;

	public void setUp() {
	  DiscreteVariable nonterm = new DiscreteVariable("nonterminals", Arrays.asList(
	      "N", "V", "S", "S2", "NP", "VP", "foo", "R", "bar", "barP", "A"));
	  DiscreteVariable terms = new DiscreteVariable("terminals", listifyWords(Arrays.asList(
	      "gretzky", "plays", "ice", "hockey", "ice hockey", "baz", "bbb", 
	      "baz bbb", "a", "b", "c")));
	  
	  parentVar = new VariableNumMap(Ints.asList(0), Arrays.asList(nonterm));
	  leftVar = new VariableNumMap(Ints.asList(1), Arrays.asList(nonterm));
	  rightVar = new VariableNumMap(Ints.asList(2), Arrays.asList(nonterm));
	  termVar = new VariableNumMap(Ints.asList(3), Arrays.asList(terms));
	  
	  VariableNumMap binaryFactorVars = parentVar.union(leftVar).union(rightVar);
	  TableFactorBuilder binaryBuilder = new TableFactorBuilder(binaryFactorVars);
	  VariableNumMap terminalFactorVars = parentVar.union(termVar);
	  TableFactorBuilder terminalBuilder = new TableFactorBuilder(terminalFactorVars);
	    
		addTerminal(terminalBuilder, "N", "gretzky", 0.25);
		addTerminal(terminalBuilder, "N", "ice hockey", 0.25);
		addTerminal(terminalBuilder, "N", "ice", 0.25);
		addTerminal(terminalBuilder, "N", "hockey", 0.25);
		addTerminal(terminalBuilder, "V", "plays", 1.0);

		addBinary(binaryBuilder, "S", "N", "VP", 1.0);
		addBinary(binaryBuilder, "S2", "N", "VP", 1.0);
		addBinary(binaryBuilder, "VP", "V", "N", 1.0);
		addBinary(binaryBuilder, "NP", "N", "N", 1.0);
		addBinary(binaryBuilder, "foo", "N", "N", 0.5);
		addBinary(binaryBuilder, "foo", "R", "S", 0.5);

		addTerminal(terminalBuilder, "bar", "baz", 0.5);
		addTerminal(terminalBuilder, "bar", "bbb", 0.5);
		addTerminal(terminalBuilder, "barP", "baz bbb", 0.5);
		addBinary(binaryBuilder, "barP", "bar", "bar", 0.5);

		addBinary(binaryBuilder, "A", "A", "A", 0.25);
		addTerminal(terminalBuilder, "A", "a", 0.25);
		addTerminal(terminalBuilder, "A", "b", 0.25);
		addTerminal(terminalBuilder, "A", "c", 0.25);

		binary = binaryBuilder.build();
		terminal = terminalBuilder.build();
		p = new CfgParser(parentVar, leftVar, rightVar, termVar, binary, terminal);
	}
	
	private void addTerminal(TableFactorBuilder terminalBuilder, String nonterm, String term, double weight) {
	  List<String> terminalValue = Arrays.asList(term.split(" "));
	  terminalBuilder.incrementWeight(terminalBuilder.getVars()
	      .outcomeArrayToAssignment(nonterm, terminalValue), weight);
	}
	
	private void addBinary(TableFactorBuilder binaryBuilder, String parent, String left, String right, double weight) {
	  binaryBuilder.incrementWeight(binaryBuilder.getVars()
	      .outcomeArrayToAssignment(parent, left, right), weight);
	}
	
	/**
	 * Splits each string in the input into component words and adds the result
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
		ParseChart c = p.parseInsideMarginal(Arrays.asList("gretzky", "plays", "ice", "hockey"), true);

		Factor rootProductions = c.getInsideEntries(0, 3);
		assertEquals(2.0, rootProductions.size());
		assertEquals(0.25 * .25, rootProductions.getUnnormalizedProbability("S"));
		assertEquals(0.25 * .25, rootProductions.getUnnormalizedProbability("S2"));

		Factor nounProductions = c.getInsideEntries(2, 3);
		assertEquals(3.0, nounProductions.size());
		assertEquals(.25, nounProductions.getUnnormalizedProbability("N"));
		assertEquals(.25 * .25, nounProductions.getUnnormalizedProbability("NP"));
		assertEquals(.5 * .25 * .25, nounProductions.getUnnormalizedProbability("foo"));
	}

	public void testParseOutsideMarginal() {
		ParseChart c = p.parseMarginal(Arrays.asList("gretzky", "plays", "ice", "hockey"), "S", true);

		Factor rootProductions = c.getOutsideEntries(0, 3);
		assertEquals(1.0, rootProductions.size());
		assertEquals(1.0, rootProductions.getUnnormalizedProbability("S"));

		Factor vpProductions = c.getOutsideEntries(1, 3);
		assertEquals(.25, vpProductions.getUnnormalizedProbability("VP"));
	}

	public void testParseMarginal() {
	  ParseChart c = p.parseMarginal(Arrays.asList("gretzky", "plays", "ice", "hockey"), "S", true);

		Factor rootProductions = c.getMarginalEntries(0, 3);
		assertEquals(1.0, rootProductions.size());
		assertEquals(1.0, rootProductions.getUnnormalizedProbability("S") / c.getPartitionFunction());

		Factor nProductions = c.getMarginalEntries(2, 3);
		assertEquals(1.0, nProductions.getUnnormalizedProbability("N") / c.getPartitionFunction());
	}

	public void testRuleCounts() {
	  ParseChart c = p.parseMarginal(Arrays.asList("gretzky", "plays", "ice", "hockey"), "S", true);

		Factor ruleCounts = c.getBinaryRuleExpectations();
		assertEquals(0.0, ruleCounts.getUnnormalizedProbability("S2", "N", "VP"));
		assertEquals(1.0, ruleCounts.getUnnormalizedProbability("S", "N", "VP") / c.getPartitionFunction());
		assertEquals(0.0, ruleCounts.getUnnormalizedProbability("NP", "N", "N"));
		assertEquals(1.0, ruleCounts.getUnnormalizedProbability("VP", "V", "N") / c.getPartitionFunction());

		Factor termCounts = c.getTerminalRuleExpectations();
		assertEquals(1.0, termCounts.getUnnormalizedProbability("N", Arrays.asList("gretzky")) / c.getPartitionFunction());
		assertEquals(0.0, termCounts.getUnnormalizedProbability("N", Arrays.asList("hockey")));
	}

	public void testAmbiguous() {
	  ParseChart c = p.parseMarginal(Arrays.asList("a", "b", "c"), "A", true);

		Factor leftProds = c.getMarginalEntries(0, 1);
		assertEquals(0.5, leftProds.getUnnormalizedProbability("A") / c.getPartitionFunction());

		leftProds = c.getMarginalEntries(0, 0);
		assertEquals(1.0, leftProds.getUnnormalizedProbability("A") / c.getPartitionFunction());

		Factor ruleCounts = c.getBinaryRuleExpectations();
		assertEquals(2.0, ruleCounts.getUnnormalizedProbability("A", "A", "A") / c.getPartitionFunction());

		Factor termCounts = c.getTerminalRuleExpectations();
		assertEquals(1.0, termCounts.getUnnormalizedProbability("A", Arrays.asList("b")) / c.getPartitionFunction());	
	}

	public void testParseMaxMarginal() {
		ParseChart c = p.parseMarginal(Arrays.asList("baz", "bbb"), "barP", false);
		Factor prods = c.getInsideEntries(0, 1);
		assertEquals(1.0, prods.size());
		assertEquals(.5, prods.getUnnormalizedProbability("barP"));	
	}

	public void testParseMaxMarginalTree() {
		ParseChart c = p.parseInsideMarginal(Arrays.asList("gretzky", "plays", "ice", "hockey"), false); 
		    
		ParseTree t = c.getBestParseTrees("S", 1).get(0);
		assertEquals("S", t.getRoot());
		assertEquals("N", t.getLeft().getRoot());
		assertEquals("VP", t.getRight().getRoot());
		assertEquals("V", t.getRight().getLeft().getRoot());
	}
	
	public void testParseMaxMarginalTreeDist() {
	  ParseChart c = p.parseInsideMarginal(Arrays.asList("gretzky", "plays", "ice", "hockey"), false);
		
		Factor rootProbabilities = TableFactor.pointDistribution(parentVar, parentVar.outcomeArrayToAssignment("S")).product(0.5)
		    .add(TableFactor.pointDistribution(parentVar, parentVar.outcomeArrayToAssignment("S2")));
		
		List<ParseTree> trees = c.getBestParseTrees(rootProbabilities, 2);
		ParseTree best = trees.get(0);
		assertEquals("S2", best.getRoot());
		assertEquals("N", best.getLeft().getRoot());
		assertEquals("VP", best.getRight().getRoot());
		assertEquals("V", best.getRight().getLeft().getRoot());
		
		ParseTree second = trees.get(1);
		assertEquals("S", second.getRoot());
		assertEquals("N", second.getLeft().getRoot());
		assertEquals("VP", second.getRight().getRoot());
		assertEquals("V", second.getRight().getLeft().getRoot());
	}

	public void testMostLikelyProductions() {
		ParseChart c = p.mostLikelyProductions("barP", 2, 2);

		List<ParseTree> trees = c.getBestParseTrees("barP", 2);
		assertEquals(0.5, trees.get(0).getProbability());
		assertTrue(trees.get(0).isTerminal());
		assertEquals(Arrays.asList("baz", "bbb"),
				trees.get(0).getTerminalProductions());
		assertEquals(0.125, trees.get(1).getProbability());
		assertEquals("bar", trees.get(1).getLeft().getRoot());
		assertEquals("bar", trees.get(1).getRight().getRoot());
	}
}
