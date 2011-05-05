package com.jayantkrish.jklol.cfg;

import com.jayantkrish.jklol.util.DefaultHashMap;

import java.util.Map;
import java.util.Set;
import java.util.HashMap;

/**
 * A CptTableProductionDistribution maintains a separate CPT for each production rule
 * (i.e., no parameters are tied across production rules).
 */
public class CptTableProductionDistribution implements CptProductionDistribution {

    private Grammar grammar;
    private DefaultHashMap<Production, Double> denominators;
    private DefaultHashMap<BinaryProduction, Double> binaryRuleCounts;
    private DefaultHashMap<TerminalProduction, Double> terminalRuleCounts;
    
    /**
     * Initialize a probability distribution over the rules in the grammar g. The initial CPTs are
     * the uniform distribution.
     */
    public CptTableProductionDistribution(Grammar g) {
	grammar = g;
	binaryRuleCounts = new DefaultHashMap<BinaryProduction, Double>(0.0);
	terminalRuleCounts = new DefaultHashMap<TerminalProduction, Double>(0.0);
	denominators = new DefaultHashMap<Production, Double>(0.0);
	addUniformSmoothing(1.0);
    }

    public double getRuleProbability(BinaryProduction rule) {
	return binaryRuleCounts.get(rule) / denominators.get(rule.getParent());
    }

    public double getTerminalProbability(TerminalProduction rule) {
	return terminalRuleCounts.get(rule) / denominators.get(rule.getParent());
    }

    /**
     * Uniformly smooths the CPTs of all production rules.
     */
    public void addUniformSmoothing(double virtualCount) {
	for (Production nonterm : grammar.getAllNonTerminals()) {
	    Set<BinaryProduction> bps = grammar.getBinaryProductions(nonterm);
	    Set<TerminalProduction> tps = grammar.getTerminalProductions(nonterm);
	    denominators.put(nonterm, (bps.size() + tps.size()) * virtualCount);
	    
	    for (BinaryProduction bp : bps) {
		binaryRuleCounts.put(bp, virtualCount);
	    }
	    for (TerminalProduction tp : tps) {
		terminalRuleCounts.put(tp, virtualCount);
	    }
	}
    }

    /**
     * Delete all sufficient statistics accumulated and stored in the CPTs.
     */ 
    public void clearCpts() {
	for (Production nonterm : grammar.getAllNonTerminals()) {
	    denominators.put(nonterm, 0.0);

	    Set<BinaryProduction> bps = grammar.getBinaryProductions(nonterm);
	    Set<TerminalProduction> tps = grammar.getTerminalProductions(nonterm);

	    for (BinaryProduction bp : bps) {
		binaryRuleCounts.put(bp, 0.0);
	    }
	    for (TerminalProduction tp : tps) {
		terminalRuleCounts.put(tp, 0.0);
	    }
	}
    }

    public void incrementBinaryCpts(Map<BinaryProduction, Double> binaryRuleExpectations, double count) {
	for (BinaryProduction bp : binaryRuleExpectations.keySet()) {
	    Production parent = bp.getParent();
	    denominators.put(parent, denominators.get(parent) + (count * binaryRuleExpectations.get(bp)));
	    binaryRuleCounts.put(bp, binaryRuleCounts.get(bp) + (count * binaryRuleExpectations.get(bp)));
	}
    }

    public void incrementTerminalCpts(Map<TerminalProduction, Double> terminalRuleExpectations, double count) {
	for (TerminalProduction tp : terminalRuleExpectations.keySet()) {
	    Production parent = tp.getParent();
	    denominators.put(parent, denominators.get(parent) + (count * terminalRuleExpectations.get(tp)));
	    terminalRuleCounts.put(tp, terminalRuleCounts.get(tp) + (count * terminalRuleExpectations.get(tp)));
	}
    }

    public String toString() {
	StringBuilder sb = new StringBuilder();
	for (Production p : denominators.keySet()) {
	    for (BinaryProduction bp : grammar.getBinaryProductions(p)) {
		sb.append(binaryRuleCounts.get(bp) / denominators.get(p));
		sb.append(" : ");
		sb.append(bp.toString());
		sb.append("\n");
	    }

	    for (TerminalProduction tp : grammar.getTerminalProductions(p)) {
		sb.append(terminalRuleCounts.get(tp) / denominators.get(p));
		sb.append(" : ");
		sb.append(tp.toString());
		sb.append("\n");
	    }
	}
	return sb.toString();
    }
}
