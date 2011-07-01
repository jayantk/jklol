package com.jayantkrish.jklol.cfg;

/**
 * A binary production rule in a CFG
 */
public class BinaryProduction {

    public Production parent;
    public Production leftChild;
    public Production rightChild;

    public BinaryProduction(Production parent, Production leftChild, Production rightChild) {
	this.parent = parent;
	this.leftChild = leftChild;
	this.rightChild = rightChild;
    }

    public Production getParent() {
	return parent;
    }

    public Production getLeft() {
	return leftChild;
    }

    public Production getRight() {
	return rightChild;
    }

    public int hashCode() {
	return parent.hashCode() * 723 + leftChild.hashCode() * 323 + rightChild.hashCode() * 7;
    }

    public boolean equals(Object o) {
	if (o instanceof BinaryProduction) {
	    BinaryProduction p = (BinaryProduction) o;
	    return parent.equals(p.parent) && leftChild.equals(p.leftChild) && rightChild.equals(p.rightChild);
	}
	return false;
    }

    public String toString() {
	return parent.toString() + " --> " + leftChild.toString() + " " + rightChild.toString();
    }
}