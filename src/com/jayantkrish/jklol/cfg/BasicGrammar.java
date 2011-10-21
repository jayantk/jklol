package com.jayantkrish.jklol.cfg;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Sets;

/**
 * A set of binary and terminal production rules for a CFG. {@code BasicGrammar}
 * performs no rule pruning during parsing, returning all binary production
 * rules at each parse chart entry.
 * 
 * {@code BasicGrammar} represents a grammar in Chomsky normal form, except that
 * terminal production rules are allowed to produce multiple terminal symbols.
 */
public class BasicGrammar implements Grammar {

  private HashMultimap<Production, BinaryProduction> parentProductionMap;
  private Map<Production, HashMultimap<Production, BinaryProduction>> childProductionMap;
  private Set<BinaryProduction> allBinaryProductions;

  private HashMultimap<Production, TerminalProduction> terminalProductions;
  private HashMultimap<List<Production>, TerminalProduction> terminalParents;

  /**
   * Create an empty grammar with no production rules.
   */
  public BasicGrammar() {
    parentProductionMap = HashMultimap.create();
    childProductionMap = new HashMap<Production, HashMultimap<Production, BinaryProduction>>();
    allBinaryProductions = new HashSet<BinaryProduction>();

    terminalProductions = HashMultimap.create();
    terminalParents = HashMultimap.create();    
  }

  /**
   * Copy constructor.
   * 
   * @param other
   */
  public BasicGrammar(BasicGrammar other) {
    parentProductionMap = HashMultimap.create();
    childProductionMap = new HashMap<Production, HashMultimap<Production, BinaryProduction>>();
    for (Production root : other.childProductionMap.keySet()) {
      childProductionMap.put(root, HashMultimap.<Production, BinaryProduction>create(
          other.childProductionMap.get(root)));
    }
    allBinaryProductions = new HashSet<BinaryProduction>(other.allBinaryProductions);

    terminalProductions = HashMultimap.create();
    terminalParents = HashMultimap.create();
  }
  
  @Override
  public Set<BinaryProduction> getBinaryProductionsForEntry(int spanStart, int spanEnd, int splitIndex) {
    return getBinaryProductions();
  }
  
  @Override
  public Set<TerminalProduction> getTerminalSpanProductions(List<Production> terminals, int spanStart, int spanEnd) {
    List<Production> spanProductions = terminals.subList(spanStart, spanEnd + 1);
    return getTerminalSpanParents(spanProductions);
  }

  @Override
  public Set<TerminalProduction> getAllTerminalProductions() {
    return Sets.newHashSet(terminalProductions.values());
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
      childProductionMap.put(rule.getLeft(), HashMultimap.<Production, BinaryProduction>create());
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
   * Get all terminal production rule which can produce a given production span.
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
   * Get all non-terminal productions in the grammar.
   */
  public Set<Production> getAllNonTerminals() {
    Set<Production> nonterminals = new HashSet<Production>();
    nonterminals.addAll(terminalProductions.keySet());
    nonterminals.addAll(parentProductionMap.keySet());
    return nonterminals;
  }

}