package com.jayantkrish.jklol.ccg;

public class CcgRuleSchema {
  private final SyntacticCategory left;
  private final SyntacticCategory right;
  private final SyntacticCategory root;

  public CcgRuleSchema(SyntacticCategory left, SyntacticCategory right, SyntacticCategory root) {
    this.left = left;
    this.right = right;
    this.root = root;
  }
  public SyntacticCategory getLeft() {
    return left;
  }
  public SyntacticCategory getRight() {
    return right;
  }
  public SyntacticCategory getRoot() {
    return root;
  }
  
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((left == null) ? 0 : left.hashCode());
    result = prime * result + ((right == null) ? 0 : right.hashCode());
    result = prime * result + ((root == null) ? 0 : root.hashCode());
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
    CcgRuleSchema other = (CcgRuleSchema) obj;
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
    return true;
  }
}
