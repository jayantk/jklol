package com.jayantkrish.jklol.cfg;

import java.util.Arrays;
import java.util.List;
import java.util.Collections;

/**
 * A production rule in a CFG which results in terminal symbols.
 *
 * Unlike terminal rules in Chomsky normal form, TerminalProductions can produce more than one
 * terminal symbol.
 */
public class TerminalProduction {

	public Production parent;
	public List<Production> terminals;

	/**
	 * Convenience constructor for unary productions
	 */
	public TerminalProduction(Production parent, Production child) {
		this.parent = parent;
		this.terminals = Arrays.asList(new Production[] {child});
	}

	/**
	 * Constructor for arbitrary n-ary productions.
	 */
	public TerminalProduction(Production parent, List<Production> terminals) {
		this.parent = parent;
		this.terminals = terminals;
	}

	public Production getParent() {
		return parent;
	}

	public List<Production> getTerminals() {
		return Collections.unmodifiableList(terminals);
	}

	public int hashCode() {
		return parent.hashCode() * 7323 + terminals.hashCode();
	}

	public boolean equals(Object o) {
		if (o instanceof TerminalProduction) {
			TerminalProduction p = (TerminalProduction) o;
			return parent.equals(p.parent) && terminals.equals(p.terminals);
		}
		return false;
	}

	public String toString() {
		return parent.toString() + " --> " + terminals.toString();
	}
}

