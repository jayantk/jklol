package com.jayantkrish.jklol.cfg;

import java.util.List;
import java.util.ArrayList;

public class ParseTree implements Comparable<ParseTree> {

    private ParseTree left;
    private ParseTree right;

    private Production production;

    private TerminalProduction tp;
    private BinaryProduction bp;

    private double prob;

    public ParseTree(ParseTree left, ParseTree right, BinaryProduction bp, double prob) {
	production = bp.getParent();
	this.bp = bp;
	this.left = left;
	this.right = right;
	this.tp = null;
	this.prob = prob;
    }

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
