package com.jayantkrish.jklol.util;

/**
 * A pair.
 */
public class Pair<L, R> {

	private L left;
	private R right;

	public Pair(L left, R right) {
		this.left = left;
		this.right = right;
	}

	public L getLeft() {
		return left;
	}

	public R getRight() {
		return right;
	}

	public int hashCode() {
		return left.hashCode() * 748123 + right.hashCode() * 331;
	}

	public boolean equals(Object o) {
		if (o instanceof Pair<?,?>) {
			Pair<?,?> p = (Pair<?,?>) o;
			return left.equals(p.left) && right.equals(p.right);
		}
		return false;
	}
	
	public String toString() {
		return "<" + getLeft() + ", " + getRight() + ">"; 
	}
}

