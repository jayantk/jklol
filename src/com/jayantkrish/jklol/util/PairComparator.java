package com.jayantkrish.jklol.util;

import java.util.Comparator;

/**
 * A way of comparing pairs. Pairs are sorted by their left entry.
 */ 
public class PairComparator<L extends Comparable<L>, R> implements Comparator<Pair<L, R>> {

	public PairComparator() {}

	public int compare(Pair<L,R> o1, Pair<L,R> o2) {
		return o1.getLeft().compareTo(o2.getLeft());
	}
}