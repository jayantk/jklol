package com.jayantkrish.jklol.cfg;

/**
 * A pointer back to previous entries in a parse chart. Used for recovering maximum
 * probability parse trees / performing beam searches.
 *
 * In order to enable easy beam searching, Backpointers are comparable.
 */
public class Backpointer implements Comparable<Backpointer> {

    // These variables are valid if the backpointer represents the application of
    // a binary production rule.
    private int splitInd;
    private BinaryProduction bp;

    // Valid if the backpointer represents a terminal production
    private TerminalProduction tp;

    private double probability;
    private double ruleProbability;

    public Backpointer(int splitInd, BinaryProduction bp, 
	    double probability, double ruleProbability) {
	this.splitInd = splitInd;
	this.bp = bp;
	this.probability = probability;
	this.ruleProbability = ruleProbability;
	tp = null;
    }

    public Backpointer(TerminalProduction tp, double probability, double ruleProbability) {
	this.tp = tp;
	this.probability = probability;
	this.ruleProbability = ruleProbability;
	splitInd = 0;
	bp = null;
    }

    public boolean isTerminal() {
	return tp != null;
    }

    public int getSplitInd() {
	assert !isTerminal();
	return splitInd;
    }

    public BinaryProduction getBinaryProduction() {
	assert !isTerminal();
	return bp;
    }

    public TerminalProduction getTerminalProduction() {
	assert isTerminal();
	return tp;
    }

    public double getProbability() {
	return probability;
    }

    public double getRuleProbability() {
	return ruleProbability;
    }

    public int compareTo(Backpointer other) {
	return Double.compare(this.probability, other.probability);
    }
}
