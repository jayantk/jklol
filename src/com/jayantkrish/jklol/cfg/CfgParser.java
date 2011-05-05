package com.jayantkrish.jklol.cfg;

import java.util.*;

/**
 * A CKY-style parser for probabilistic context free grammars in Chomsky normal form (with a
 * multi-terminal production extension).
 */ 
public class CfgParser {

    private Grammar grammar;
    private ProductionDistribution probs;

    /**
     * Create a CFG parser that parses using the specified grammar.
     */ 
    public CfgParser(Grammar grammar, ProductionDistribution probs) {
	this.grammar = grammar;
	this.probs = probs;
    }

    public Grammar getGrammar() {
	return grammar;
    }

    public ProductionDistribution getDistribution() {
	return probs;
    }

    /**
     * Set a probability distribution over the productions in the CFG's grammar.
     */
    public void setDistribution(ProductionDistribution probs) {
	this.probs = probs;
    }

    ////////////////////////////////////////////////////////////////////////
    // The following methods are the important ones for running the parser in isolation.
    ////////////////////////////////////////////////////////////////////////
    
    /**
     * Compute the marginal distribution over all grammar entries 
     * conditioned on the given sequence of terminals.
     */
    public ParseChart parseMarginal(List<Production> terminals, Production root) {
	ParseChart chart = new ParseChart(terminals.size(), true);
	Map<List<Production>, Double> terminalMap = new HashMap<List<Production>, Double>();
	terminalMap.put(terminals, 1.0);
	Map<Production, Double> productionMap = new HashMap<Production, Double>();
	productionMap.put(root, 1.0);
	return marginal(chart, terminalMap, productionMap);

    }

    /**
     * Compute the distribution over CFG entries, the parse root, and the children, accounting for
     * the provided distributions over the root and terminals.
     *
     * Assumes all terminals in terminalDist have the same number of productions.
     */ 
    public ParseChart parseMarginal(Map<List<Production>, Double> terminalDist,
	    Map<Production, Double> rootDist) {
	List<Production> term = terminalDist.keySet().iterator().next();
	return marginal(new ParseChart(term.size(), true), terminalDist, rootDist);
    }

    /**
     * Compute the max-marginal distribution over grammar entries at the root of the parse
     * tree. This method can be used to compute the most likely parse.
     */ 
    public ParseChart parseMaxMarginal(List<Production> terminals, Production root) {
	ParseChart chart = new ParseChart(terminals.size(), false);
	Map<List<Production>, Double> terminalMap = new HashMap<List<Production>, Double>();
	terminalMap.put(terminals, 1.0);
	Map<Production, Double> productionMap = new HashMap<Production, Double>();
	productionMap.put(root, 1.0);
	return marginal(chart, terminalMap, productionMap);
    }

    /**
     * Compute the distribution over CFG entries, the parse root, and the children, accounting for
     * the provided distributions over the root and terminals.
     *
     * Assumes all terminals in terminalDist have the same number of productions.
     */ 
    public ParseChart parseMaxMarginal(Map<List<Production>, Double> terminalDist,
	    Map<Production, Double> rootDist) {
	List<Production> term = terminalDist.keySet().iterator().next();
	return marginal(new ParseChart(term.size(), false), terminalDist, rootDist);
    }


    /**
     * Compute the most likely sequence of productions (of a given length) conditioned on the root symbol.
     */
    public ParseChart mostLikelyProductions(Production root, int length, int beamWidth) {
	ParseChart chart = new ParseChart(length, false);
	chart.setBeamWidth(beamWidth);
	initializeChartAllTerminals(chart);
	upwardChartPass(chart);
	chart.updateOutsideEntry(0, chart.chartSize() - 1, root, 1.0);
	downwardChartPass(chart);
	return chart;
    }

    //////////////////////////////////////////////////////////////////////////////////
    // Methods for computing partial parse distributions, intended mostly for running
    // the CFG parser as part of a graphical model.
    //////////////////////////////////////////////////////////////////////////////////

    /**
     * Calculate the inside probabilities (i.e., run the upward pass of variable elimination).
     */
    public ParseChart parseInsideMarginal(List<Production> terminals, boolean useSumProduct) {
	Map<List<Production>, Double> termDist = new HashMap<List<Production>, Double>();
	termDist.put(terminals, 1.0);
	return parseInsideMarginal(termDist, useSumProduct);
    }

    /**
     * Calculate the inside probabilities (i.e., run the upward pass of variable elimination).
     */
    public ParseChart parseInsideMarginal(Map<List<Production>, Double> terminalDist, boolean useSumProduct) {
	List<Production> term = terminalDist.keySet().iterator().next();
	ParseChart chart = new ParseChart(term.size(), useSumProduct);
	initializeChart(chart, terminalDist);
	upwardChartPass(chart);
	return chart;
    }

    public ParseChart parseOutsideMarginal(ParseChart chart, Map<Production, Double> rootDist) {
	assert chart.getInsideCalculated();
	assert !chart.getOutsideCalculated();
	// Set the initial outside probabilities
	for (Production root : rootDist.keySet()) {
	    chart.updateOutsideEntry(0, chart.chartSize() - 1, root, rootDist.get(root));
	}
	downwardChartPass(chart);
	return chart;
    }

    ////////////////////////////////////////////////////////////////////////////////////
    // Misc
    ////////////////////////////////////////////////////////////////////////////////////

    public String toString() {
	return probs.toString();
    }

    /*
     * Helper method for computing marginals / max-marginals with an arbitrary distribution on
     * terminals.
     */
    private ParseChart marginal(ParseChart chart, Map<List<Production>, Double> terminalDist,
	    Map<Production, Double> rootDist) {

	initializeChart(chart, terminalDist);
	upwardChartPass(chart);

	// Set the initial outside probability
	for (Production root : rootDist.keySet()) {
	    chart.updateOutsideEntry(0, chart.chartSize() - 1, root, rootDist.get(root));
	}

	downwardChartPass(chart);
	return chart;
    }

    /*
     * This method calculates all of the inside probabilities by iteratively 
     * parsing larger and larger spans of the sentence.
     */ 
    private void upwardChartPass(ParseChart chart) {
	// spanSize is the number of words *in addition* to the word under
	// spanStart.
	for (int spanSize = 1; spanSize < chart.chartSize(); spanSize++) {
	    for (int spanStart = 0; spanStart + spanSize < chart.chartSize(); spanStart++) {
		int spanEnd = spanStart + spanSize;
		calculateInside(spanStart, spanEnd, chart);
	    }
	}
	chart.setInsideCalculated();
    }

    /*
     * Calculate a single inside probability entry.
     */
    private void calculateInside(int spanStart, int spanEnd, ParseChart chart) {
	for (int k = 0; k < spanEnd - spanStart; k++) {
	    
	    Map<Production, Double> left = chart.getInsideEntries(spanStart, spanStart + k);
	    Map<Production, Double> right = chart.getInsideEntries(spanStart + k + 1, spanEnd);

	    //for (Production leftP : left.keySet()) {
	    //for (Production rightP : right.keySet()) {
		    for (BinaryProduction rule : grammar.getBinaryProductions()) {
			if (left.containsKey(rule.getLeft()) && right.containsKey(rule.getRight())) {
			    chart.updateInsideEntry(spanStart, spanEnd, k, rule, rule.getParent(), 
				    left.get(rule.getLeft()) * right.get(rule.getRight()) * probs.getRuleProbability(rule),
				    probs.getRuleProbability(rule));
			}
		    }
		    //}
		    //	}
	}
    }

    /*
     * Compute the outside probabilities moving downward from the top of the tree.
     */
    private void downwardChartPass(ParseChart chart) {
	assert chart.getInsideCalculated();
	
	// Calculate root marginal, which is not included in the rest of the pass.
	// Also compute the partition function.
	double partitionFunction = 0.0;
	Map<Production, Double> rootOutside = chart.getOutsideEntries(0, chart.chartSize() - 1);
	Map<Production, Double> rootInside = chart.getInsideEntries(0, chart.chartSize() - 1);
	for (Production p : rootOutside.keySet()) {
	    if (rootInside.containsKey(p)) {
		chart.updateMarginalEntry(0, chart.chartSize() - 1, p, rootOutside.get(p) * rootInside.get(p));
		partitionFunction += rootOutside.get(p) * rootInside.get(p);
	    }
	}
	chart.setPartitionFunction(partitionFunction);
	
	for (int spanSize = chart.chartSize() - 1; spanSize >= 1; spanSize--) {
	    for (int spanStart = 0; spanStart + spanSize < chart.chartSize(); spanStart++) {
		int spanEnd = spanStart + spanSize;
		calculateOutside(spanStart, spanEnd, chart);
	    }
	}

	updateTerminalRuleCounts(chart);

	// Outside probabilities / partition function are now calculated.
	chart.setOutsideCalculated();
    }

    /*
     * Calculate a single outside probability entry (and its corresponding marginal).
     */
    private void calculateOutside(int spanStart, int spanEnd, ParseChart chart) {
	Map<Production, Double> parent = chart.getOutsideEntries(spanStart, spanEnd);
	for (int k = 0; k < spanEnd - spanStart; k++) {
	    Map<Production, Double> left = chart.getInsideEntries(spanStart, spanStart + k);
	    Map<Production, Double> right = chart.getInsideEntries(spanStart + k + 1, spanEnd);

	    // for (Production leftP : left.keySet()) {
	    // for (Production rightP : right.keySet()) {
		    for (BinaryProduction rule : grammar.getBinaryProductions()) {
			if (left.containsKey(rule.getLeft()) && right.containsKey(rule.getRight()) && 
				parent.containsKey(rule.getParent())) {
			    chart.updateOutsideEntry(spanStart, spanStart + k, rule.getLeft(), 
				    right.get(rule.getRight()) * parent.get(rule.getParent()) * probs.getRuleProbability(rule));
			    chart.updateOutsideEntry(spanStart + k + 1, spanEnd, rule.getRight(), 
				    left.get(rule.getLeft()) * parent.get(rule.getParent()) * probs.getRuleProbability(rule));
			    
			    double cliqueMarginal = left.get(rule.getLeft()) * right.get(rule.getRight()) * 
				    parent.get(rule.getParent()) * probs.getRuleProbability(rule);
			    chart.updateMarginalEntry(spanStart, spanStart + k, rule.getLeft(), cliqueMarginal);
			    chart.updateMarginalEntry(spanStart + k + 1, spanEnd, rule.getRight(), cliqueMarginal);
			    
			    chart.updateBinaryRuleExpectation(rule, cliqueMarginal);
			}
		    }
		    //}
		    //}
	}
    }


    /*
     * Fill in the initial chart entries implied by the given set of terminals.
     */
    private void initializeChart(ParseChart chart, Map<List<Production>, Double> terminalMap) {
	chart.setTerminalDist(terminalMap);

	for (List<Production> terminals : terminalMap.keySet()) {
	    double prob = terminalMap.get(terminals);
	    for (int i = 0; i < terminals.size(); i++) {
		for (int j = i; j < terminals.size(); j++) {
		    Set<TerminalProduction> terminalParents = grammar.getTerminalSpanParents(terminals, i, j);
		    
		    for (TerminalProduction terminal : terminalParents) {
			// System.out.println(terminal + ":" + probs.getTerminalProbability(terminal));
			chart.updateInsideEntryTerminal(i, j, terminal, terminal.getParent(), 
				prob * probs.getTerminalProbability(terminal),
				probs.getTerminalProbability(terminal));
		    }
		}
	    }
	}
    }

    /*
     * Fill in the chart using all terminals.
     */
    private void initializeChartAllTerminals(ParseChart chart) {
	chart.setTerminalDist(null);

	for (TerminalProduction tp : grammar.getAllTerminalProductionRules()) {
	    List<Production> children = tp.getTerminals();
	    int spanSize = children.size() - 1;
	    for (int i = 0; i < chart.chartSize() - spanSize; i++) {
		chart.updateInsideEntryTerminal(i, i + spanSize, tp, 
			tp.getParent(), probs.getTerminalProbability(tp), probs.getTerminalProbability(tp));
	    }
	}
    }

    private void updateTerminalRuleCounts(ParseChart chart) {
	Map<List<Production>, Double> terminalMap = chart.getTerminalDist();
	if (terminalMap != null) {
	    for (List<Production> terminals : terminalMap.keySet()) {
		double prob = terminalMap.get(terminals);
		for (int i = 0; i < terminals.size(); i++) {
		    for (int j = i; j < terminals.size(); j++) {
			Set<TerminalProduction> terminalParents = grammar.getTerminalSpanParents(terminals, i, j);
			Map<Production, Double> nontermDist = chart.getOutsideEntries(i, j);
			
			for (TerminalProduction terminal : terminalParents) {
			    Production parent = terminal.getParent();
			    if (nontermDist.containsKey(parent)) {
				// System.out.println(terminal + ":" + probs.getTerminalProbability(terminal));
				chart.updateTerminalRuleExpectation(terminal, 
					nontermDist.get(parent) * probs.getTerminalProbability(terminal) * prob);
			    }
			}
		    }
		}
	    }
	}
    }
}