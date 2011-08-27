package com.jayantkrish.jklol.cfg;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

public class CfgParserTest extends TestCase {

	Grammar g;
	TerminalProduction multi;
	CfgParser p;
	CptProductionDistribution dist;

	private Production prod(String s) {
		return Production.getProduction(s);
	}

	private TerminalProduction term(String p, String c) {
		return new TerminalProduction(prod(p), prod(c));
	}

	private BinaryProduction bp(String p, String l, String r) {
		return new BinaryProduction(prod(p), prod(l), prod(r));
	}

	public void setUp() {

		g = new Grammar();

		g.addTerminal(term("N", "gretzky"));
		g.addTerminal(term("V", "plays"));
		multi = new TerminalProduction(prod("N"), 
				Arrays.asList(new Production[] {prod("ice"), prod("hockey")}));
		g.addTerminal(multi);
		g.addTerminal(term("N", "ice"));
		g.addTerminal(term("N", "hockey"));

		g.addProductionRule(bp("S2", "N", "VP"));
		g.addProductionRule(bp("S", "N", "VP"));
		g.addProductionRule(bp("VP", "V", "N"));
		g.addProductionRule(bp("NP", "N", "N"));
		g.addProductionRule(bp("foo", "N", "N"));
		g.addProductionRule(bp("foo", "R", "S"));


		g.addTerminal(term("bar", "baz"));
		g.addTerminal(term("bar", "bbb"));
		g.addTerminal(new TerminalProduction(prod("barP"), 
				Arrays.asList(new Production[] {prod("baz"), prod("bbb")})));

		g.addProductionRule(bp("barP", "bar", "bar"));

		g.addProductionRule(bp("A", "A", "A"));
		g.addTerminal(term("A", "a"));
		g.addTerminal(term("A", "b"));
		g.addTerminal(term("A", "c"));

		dist = new CptTableProductionDistribution(g);
		dist.increment(1.0);
		p = new CfgParser(g, dist);
	}

	public void testParseInsideMarginal() {
		ParseChart c = p.parseInsideMarginal(Arrays.asList(new Production[] 
		                                                                  {prod("gretzky"), prod("plays"), prod("ice"), prod("hockey")}), true);

		Map<Production, Double> rootProductions = c.getInsideEntries(0, 3);
		assertEquals(2, rootProductions.size());
		assertEquals(0.25 * .25, rootProductions.get(prod("S")));
		assertEquals(0.25 * .25, rootProductions.get(prod("S2")));

		Map<Production, Double> nounProductions = c.getInsideEntries(2, 3);
		assertEquals(3, nounProductions.size());
		assertEquals(.25, nounProductions.get(prod("N")));
		assertEquals(.25 * .25, nounProductions.get(prod("NP")));
		assertEquals(.5 * .25 * .25, nounProductions.get(prod("foo")));
	}

	public void testParseOutsideMarginal() {
		ParseChart c = p.parseMarginal(Arrays.asList(new Production[] 
		                                                            {prod("gretzky"), prod("plays"), prod("ice"), prod("hockey")}),
		                                                            prod("S"));

		Map<Production, Double> rootProductions = c.getOutsideEntries(0, 3);
		assertEquals(1, rootProductions.size());
		assertEquals(1.0, rootProductions.get(prod("S")));

		Map<Production, Double> vpProductions = c.getOutsideEntries(1, 3);
		assertEquals(.25, vpProductions.get(prod("VP")));
	}

	public void testParseMarginal() {
		ParseChart c = p.parseMarginal(Arrays.asList(new Production[] 
		                                                            {prod("gretzky"), prod("plays"), prod("ice"), prod("hockey")}),
		                                                            prod("S"));

		Map<Production, Double> rootProductions = c.getMarginalEntries(0, 3);
		assertEquals(1, rootProductions.size());
		assertEquals(1.0, rootProductions.get(prod("S")) / c.getPartitionFunction());

		Map<Production, Double> nProductions = c.getMarginalEntries(2, 3);
		assertEquals(1.0, nProductions.get(prod("N")) / c.getPartitionFunction());
	}

	public void testRuleCounts() {
		ParseChart c = p.parseMarginal(Arrays.asList(new Production[] 
		                                                            {prod("gretzky"), prod("plays"), prod("ice"), prod("hockey")}),
		                                                            prod("S"));

		Map<BinaryProduction, Double> ruleCounts = c.getBinaryRuleExpectations();
		assertEquals(null, ruleCounts.get(bp("S2", "N", "VP")));
		assertEquals(1.0, ruleCounts.get(bp("S", "N", "VP")) / c.getPartitionFunction());
		assertEquals(null, ruleCounts.get(bp("NP", "N", "N")));
		assertEquals(1.0, ruleCounts.get(bp("VP", "V", "N")) / c.getPartitionFunction());

		Map<TerminalProduction, Double> termCounts = c.getTerminalRuleExpectations();
		assertEquals(1.0, termCounts.get(term("N", "gretzky")) / c.getPartitionFunction());
		assertEquals(null, termCounts.get(term("N", "hockey")));
	}

	public void testAmbiguous() {
		ParseChart c = p.parseMarginal(Arrays.asList(new Production[] 
		                                                            {prod("a"), prod("b"), prod("c")}), prod("A"));

		Map<Production, Double> leftProds = c.getMarginalEntries(0, 1);
		assertEquals(0.5, leftProds.get(prod("A")) / c.getPartitionFunction());

		leftProds = c.getMarginalEntries(0, 0);
		assertEquals(1.0, leftProds.get(prod("A")) / c.getPartitionFunction());

		Map<BinaryProduction, Double> ruleCounts = c.getBinaryRuleExpectations();
		assertEquals(2.0, ruleCounts.get(bp("A", "A", "A")) / c.getPartitionFunction());

		Map<TerminalProduction, Double> termCounts = c.getTerminalRuleExpectations();
		assertEquals(1.0, termCounts.get(term("A", "b")) / c.getPartitionFunction());	
	}

	public void testParseMaxMarginal() {
		ParseChart c = p.parseMaxMarginal(Arrays.asList(new Production[] 
		                                                               {prod("baz"), prod("bbb")}), prod("barP"));
		Map<Production, Double> prods = c.getInsideEntries(0, 1);
		assertEquals(1, prods.size());
		assertEquals(.5, prods.get(prod("barP")));	
	}

	public void testParseMaxMarginalTree() {
		ParseChart c = p.parseInsideMarginal(Arrays.asList(new Production[] 
		                                                                  {prod("gretzky"), prod("plays"), prod("ice"), prod("hockey")}), false);
		ParseTree t = c.getBestParseTrees(prod("S"), 1).get(0);
		assertEquals(prod("S"), t.getRoot());
		assertEquals(prod("N"), t.getLeft().getRoot());
		assertEquals(prod("VP"), t.getRight().getRoot());
		assertEquals(prod("V"), t.getRight().getLeft().getRoot());
	}

	public void testMostLikelyProductions() {
		ParseChart c = p.mostLikelyProductions(prod("barP"), 2, 2);

		List<ParseTree> trees = c.getBestParseTrees(prod("barP"), 2);
		assertEquals(0.5, trees.get(0).getProbability());
		assertTrue(trees.get(0).isTerminal());
		assertEquals(Arrays.asList(new Production[] {prod("baz"), prod("bbb")}),
				trees.get(0).getTerminalProductions());
		assertEquals(0.125, trees.get(1).getProbability());
		assertEquals(prod("bar"), trees.get(1).getLeft().getRoot());
		assertEquals(prod("bar"), trees.get(1).getRight().getRoot());
	}
}
