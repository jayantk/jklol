package com.jayantkrish.jklol.cfg;

import java.util.ArrayList;
import java.util.List;

/**
 * A CFG parse tree.
 */
public class CfgParseTree implements Comparable<CfgParseTree> { 

  private final Object root;
  // Field for extra information associated with the current parse tree rule.
  private final Object ruleType;
  private final List<Object> terminal;

  private final CfgParseTree left;
  private final CfgParseTree right;

  private final double prob;
  // Span covered by this tree. Both indexes are inclusive, i.e., a one-word
  // span has spanStart == spanEnd
  private final int spanStart;
  private final int spanEnd;
  
  public static final CfgParseTree EMPTY = new CfgParseTree(null, null, null, 1.0, -1, -1);

  /**
   * Create a new parse tree by composing two subtrees with the production rule
   * {@code ruleType}, resulting in a tree rooted at {@code root}.
   */
  public CfgParseTree(Object root, Object ruleType, CfgParseTree left, CfgParseTree right,
      double prob) {
    this.root = root;
    this.ruleType = ruleType;
    this.left = left;
    this.right = right;
    this.terminal = null;
    this.prob = prob;
    this.spanStart = left.spanStart;
    this.spanEnd = right.spanEnd;
  }

  /**
   * Create a new terminal parse tree with a terminal production rule.
   */
  public CfgParseTree(Object root, Object ruleType, List<Object> terminal, double prob,
      int spanStart, int spanEnd) {
    this.root = root;
    this.ruleType = ruleType;
    this.left = null;
    this.right = null;
    this.terminal = terminal;
    this.prob = prob;
    this.spanStart = spanStart;
    this.spanEnd = spanEnd;
  }

  public double getProbability() {
    return prob;
  }
  
  public int getSpanStart() {
    return spanStart;
  }

  public int getSpanEnd() {
    return spanEnd;
  }

  public int compareTo(CfgParseTree other) {
    return Double.compare(this.prob, other.prob);
  }

  /**
   * Returns true if this tree has no subtrees associated with it (i.e., it's a
   * leaf).
   */
  public boolean isTerminal() {
    return terminal != null;
  }

  /**
   * Get the node at the root of the parse tree.
   */
  public Object getRoot() {
    return root;
  }

  /**
   * Get the left subtree. Requires the tree to be non-terminal.
   */
  public CfgParseTree getLeft() {
    assert !isTerminal();
    return left;
  }

  /**
   * Get the right subtree. Requires the tree to be non-terminal.
   */
  public CfgParseTree getRight() {
    assert !isTerminal();
    return right;
  }

  public Object getRuleType() {
    return ruleType;
  }

  /**
   * Gets a new parse tree equivalent to this one, with probability
   * {@code this.probability * amount}.
   * 
   * @param amount
   * @return
   */
  public CfgParseTree multiplyProbability(double amount) {
    if (isTerminal()) {
      return new CfgParseTree(root, ruleType, terminal, getProbability() * amount, spanStart, spanEnd);
    } else {
      return new CfgParseTree(root, ruleType, left, right, getProbability() * amount);
    }
  }

  public List<Object> getTerminalProductions() {
    List<Object> prods = new ArrayList<Object>();
    getTerminalProductions(prods);
    return prods;
  }

  public void getTerminalProductions(List<Object> toAppend) {
    if (isTerminal()) {
      toAppend.addAll(terminal);
    } else {
      left.getTerminalProductions(toAppend);
      right.getTerminalProductions(toAppend);
    }
  }

  @Override
  public String toString() {
    if (this == CfgParseTree.EMPTY) {
      return "ParseTree.EMPTY";
    } else if (!isTerminal()) {
      return "(" + root + " --" + ruleType + "--> " + left.toString() + " " + right.toString() + ")";
    }
    return "(" + root + "--" + ruleType + "-->" + terminal + ":" + spanStart + "," + spanEnd + ")";
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((left == null) ? 0 : left.hashCode());
    result = prime * result + ((right == null) ? 0 : right.hashCode());
    result = prime * result + ((root == null) ? 0 : root.hashCode());
    result = prime * result + ((ruleType == null) ? 0 : ruleType.hashCode());
    result = prime * result + spanEnd;
    result = prime * result + spanStart;
    result = prime * result + ((terminal == null) ? 0 : terminal.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    CfgParseTree other = (CfgParseTree) obj;
    if (left == null) {
      if (other.left != null)
        return false;
    } else if (!left.equals(other.left))
      return false;
    if (right == null) {
      if (other.right != null)
        return false;
    } else if (!right.equals(other.right))
      return false;
    if (root == null) {
      if (other.root != null)
        return false;
    } else if (!root.equals(other.root))
      return false;
    if (ruleType == null) {
      if (other.ruleType != null)
        return false;
    } else if (!ruleType.equals(other.ruleType))
      return false;
    if (spanEnd != other.spanEnd)
      return false;
    if (spanStart != other.spanStart)
      return false;
    if (terminal == null) {
      if (other.terminal != null)
        return false;
    } else if (!terminal.equals(other.terminal))
      return false;
    return true;
  }
}
