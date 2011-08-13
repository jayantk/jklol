package com.jayantkrish.jklol.cfg;

import java.util.Arrays;
import java.util.Set;

import junit.framework.TestCase;

public class GrammarTest extends TestCase {

	Grammar g;
	TerminalProduction multi;

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


		g.addProductionRule(bp("S", "N", "VP"));
		g.addProductionRule(bp("VP", "V", "N"));
	}

	public void testGetBinaryProductions() {
		Set<BinaryProduction> bins = g.getBinaryProductions();
		assertTrue(bins.contains(bp("S", "N", "VP")));
		assertTrue(bins.contains(bp("VP", "V", "N")));
		assertFalse(bins.contains(bp("N", "V", "hockey")));
	}

	public void testGetTerminalSpanParents() {

		Set<TerminalProduction> terms = g.getTerminalSpanParents(
				Arrays.asList(new Production[] {prod("hockey")}), 0,0);
		assertEquals(0, terms.size());

		terms = g.getTerminalSpanParents(
				Arrays.asList(new Production[] {prod("gretzky")}), 0,0);
		assertEquals(1, terms.size());
		assertTrue(terms.contains(term("N", "gretzky")));

		terms = g.getTerminalSpanParents(
				Arrays.asList(new Production[] {prod("ice"), prod("hockey")}), 0, 1);
		assertEquals(1, terms.size());
		assertTrue(terms.contains(multi));

		terms = g.getTerminalSpanParents(
				Arrays.asList(new Production[] {prod("ice"), prod("hockey")}), 0, 0);
		assertEquals(0, terms.size());
	}


}