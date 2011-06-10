package com.jayantkrish.jklol.cfg;

import java.util.List;
import java.util.ArrayList;

/**
 * A CFG parse tree.
 */
public class ParseTree implements Comparable<ParseTree> {

    private ParseTree left;
    private ParseTree right;

    private Production production;

    private TerminalProduction tp;
    private BinaryProduction bp;

    private double prob;

    /**
     * Create a new parse tree by composing two subtrees with production rule bp.
     */
    public ParseTree(ParseTree left, ParseTree right, BinaryProduction bp, double prob) {
	production = bp.getParent();
	this.bp = bp;
	this.left = left;
	this.right = right;
	this.tp = null;
	this.prob = prob;
    }

    /**
     * Create a new terminal parse tree with a terminal production rule.
     */
    public ParseTree(TerminalProduction tp, double prob) {
	production = tp.getParent();
	this.tp = tp;
	this.prob = prob;
    }

    public double getProbability() {
	return prob;
    }

    public int compareTo(ParseTree other) {
	return Double.compare(this.prob, other.prob);
    }

    /**
     * Returns true if this tree has no subtrees associated with it (i.e., it's a leaf).
     */
    public boolean isTerminal() {
	return tp != null;
    }

    /**
     * Get the node at the root of the parse tree.
     */
    public Production getRoot() {
	if (isTerminal()) {
	    return tp.getParent();
	} else {
	    return bp.getParent();
	}
    }

    /**
     * Get the left subtree. Requires the tree to be non-terminal.
     */
    public ParseTree getLeft() {
	assert !isTerminal();
	return left;
    }

    /**
     * Get the right subtree. Requires the tree to be non-terminal.
     */
    public ParseTree getRight() {
	assert !isTerminal();
	return right;
    }

    public List<Production> getTerminalProductions() {
	List<Production> prods = new ArrayList<Production>();
	getTerminalProductions(prods);
	return prods;
    }

    public void getTerminalProductions(List<Production> toAppend) {
	if (tp != null) {
	    toAppend.addAll(tp.getTerminals());
	} else {
	    left.getTerminalProductions(toAppend);
	    right.getTerminalProductions(toAppend);
	}
    }

    public String toString() {
	if (tp == null) {
	    return "(" + production + " --> " + left.toString() + " " + right.toString() + ")";
	}
	return "(" + production + "-->" + tp.getTerminals() + ")";
    }
}
