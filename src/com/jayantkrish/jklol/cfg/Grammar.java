package com.jayantkrish.jklol.cfg;

import java.util.*;
import com.jayantkrish.jklol.util.*;

/**
 * Represents a set of production rules for a CFG.
 *
 * The Grammar is in Chomsky normal form, *except* that productions are allowed to produce multiple
 * terminal symbols.
 */
public class Grammar {

	private HashMultimap<Production, BinaryProduction> parentProductionMap;
	private Map<Production, HashMultimap<Production, BinaryProduction>> childProductionMap;
	private Set<BinaryProduction> allBinaryProductions;

	private HashMultimap<Production, TerminalProduction> terminalProductions;
	private HashMultimap<List<Production>, TerminalProduction> terminalParents;

	/**
	 * Create an empty grammar with no production rules.
	 */
	public Grammar() {
		parentProductionMap = new HashMultimap<Production, BinaryProduction>();
		childProductionMap = new HashMap<Production, HashMultimap<Production, BinaryProduction>>();
		allBinaryProductions = new HashSet<BinaryProduction>();

		terminalProductions = new HashMultimap<Production, TerminalProduction>();
		terminalParents = new HashMultimap<List<Production>, TerminalProduction>();
	}

	/**
	 * Add a terminal rule to the grammar.
	 */
	public void addTerminal(TerminalProduction term) {
		terminalProductions.put(term.getParent(), term);
		terminalParents.put(term.getTerminals(), term);
	}

	/**
	 * Add a (nonterminal) binary production rule to the grammar.
	 */ 
	public void addProductionRule(BinaryProduction rule) {
		allBinaryProductions.add(rule);
		parentProductionMap.put(rule.getParent(), rule);

		if (!childProductionMap.containsKey(rule.getLeft())) {
			childProductionMap.put(rule.getLeft(), new HashMultimap<Production, BinaryProduction>());
		}
		childProductionMap.get(rule.getLeft()).put(rule.getRight(), rule);
	}


	/**
	 * Get all binary production rules in the grammar.
	 */
	public Set<BinaryProduction> getBinaryProductions() {
		return Collections.unmodifiableSet(allBinaryProductions);
	}

	/**
	 * Get all binary production rules with a specified parent.
	 */
	public Set<BinaryProduction> getBinaryProductions(Production parent) {
		return parentProductionMap.get(parent);
	}

	public Set<BinaryProduction> getBinaryProductions(Production left, Production right) {
		if (childProductionMap.containsKey(left)) {
			return childProductionMap.get(left).get(right);
		}
		return Collections.emptySet();
	}

	/**
	 * Get all terminal production rule which can produce a given
	 * production span. spanStart and spanEnd are inclusive indices.
	 */
	public Set<TerminalProduction> getTerminalSpanParents(List<Production> terminals, int spanStart, int spanEnd) {
		List<Production> spanProductions = terminals.subList(spanStart, spanEnd + 1);
		return getTerminalSpanParents(spanProductions);
	}

	/**
	 * Get all terminal production rule which can produce a given
	 * production span.
	 */
	public Set<TerminalProduction> getTerminalSpanParents(List<Production> spanProductions) {
		return terminalParents.get(spanProductions);
	}

	/**
	 * Get all terminal productions from a given parent.
	 */
	public Set<TerminalProduction> getTerminalProductions(Production parent) {
		return terminalProductions.get(parent);
	}

	/**
	 * Get all terminal production rules.
	 */
	public Set<TerminalProduction> getAllTerminalProductionRules() {
		return terminalProductions.values();
	}

	/**
	 * Get all terminal production symbols.
	 */
	public Set<List<Production>> getAllTerminalProductions() {
		return terminalParents.keySet();
	}

	/**
	 * Get all non-terminal productions in the grammar.
	 */
	public Set<Production> getAllNonTerminals() {
		Set<Production> nonterminals = new HashSet<Production>();
		nonterminals.addAll(terminalProductions.keySet());
		nonterminals.addAll(parentProductionMap.keySet());
		return nonterminals;
	}

}