package com.jayantkrish.jklol.ccg;

import java.io.Serializable;

import com.google.common.base.Preconditions;

/**
 * A combination of unary and binary CCG combinators. A search move is
 * a way to combine a pair of adjacent nonterminals by first applying
 * the unary combinators to the left and right nonterminals, then
 * applying the binary rule to combine the results.
 * 
 * @author jayantk
 */
public class CcgSearchMove implements Serializable {
  private static final long serialVersionUID = 1L;

  // The operations which this search move combines into
  // a single operation.
  private final Combinator binaryCombinator;
  private final UnaryCombinator leftUnary;
  private final UnaryCombinator rightUnary;

  private final long binaryCombinatorKeyNum;
  private final long leftUnaryKeyNum;
  private final long rightUnaryKeyNum;

  private final int[] leftRelabeling;
  private final int[] leftInverseRelabeling;
  private final int[] rightRelabeling;
  private final int[] rightInverseRelabeling;

  public CcgSearchMove(Combinator binaryCombinator, UnaryCombinator leftUnary, UnaryCombinator rightUnary,
      long binaryCombinatorKeyNum, long leftUnaryKeyNum, long rightUnaryKeyNum, int[] leftRelabeling,
      int[] leftInverseRelabeling, int[] rightRelabeling, int[] rightInverseRelabeling) {
    this.binaryCombinator = Preconditions.checkNotNull(binaryCombinator);
    this.leftUnary = leftUnary;
    this.rightUnary = rightUnary;
    this.binaryCombinatorKeyNum = binaryCombinatorKeyNum;
    this.leftUnaryKeyNum = leftUnaryKeyNum;
    this.rightUnaryKeyNum = rightUnaryKeyNum;

    this.leftRelabeling = Preconditions.checkNotNull(leftRelabeling);
    this.leftInverseRelabeling = Preconditions.checkNotNull(leftInverseRelabeling);
    this.rightRelabeling = Preconditions.checkNotNull(rightRelabeling);
    this.rightInverseRelabeling = Preconditions.checkNotNull(rightInverseRelabeling);
    
    Preconditions.checkArgument(leftInverseRelabeling.length == rightInverseRelabeling.length);
    Preconditions.checkArgument(leftUnary != null || leftUnaryKeyNum == -1);
    Preconditions.checkArgument(rightUnary != null || rightUnaryKeyNum == -1);
  }

  public Combinator getBinaryCombinator() {
    return binaryCombinator;
  }

  public UnaryCombinator getLeftUnary() {
    return leftUnary;
  }

  public UnaryCombinator getRightUnary() {
    return rightUnary;
  }

  public long getBinaryCombinatorKeyNum() {
    return binaryCombinatorKeyNum;
  }

  public long getLeftUnaryKeyNum() {
    return leftUnaryKeyNum;
  }

  public long getRightUnaryKeyNum() {
    return rightUnaryKeyNum;
  }

  public int[] getLeftRelabeling() {
    return leftRelabeling;
  }
  
  public int[] getLeftInverseRelabeling() {
    return leftInverseRelabeling;
  }

  public int[] getRightRelabeling() {
    return rightRelabeling;
  }

  public int[] getRightInverseRelabeling() {
    return rightInverseRelabeling;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    if (leftUnary != null) {
      sb.append(leftUnary);
    }
    if (rightUnary != null) {
      sb.append(" ");
      sb.append(rightUnary);
    }
    sb.append(" ");
    sb.append(binaryCombinator);
    return sb.toString();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((binaryCombinator == null) ? 0 : binaryCombinator.hashCode());
    result = prime * result + (int) (binaryCombinatorKeyNum ^ (binaryCombinatorKeyNum >>> 32));
    result = prime * result + ((leftUnary == null) ? 0 : leftUnary.hashCode());
    result = prime * result + (int) (leftUnaryKeyNum ^ (leftUnaryKeyNum >>> 32));
    result = prime * result + ((rightUnary == null) ? 0 : rightUnary.hashCode());
    result = prime * result + (int) (rightUnaryKeyNum ^ (rightUnaryKeyNum >>> 32));
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
    CcgSearchMove other = (CcgSearchMove) obj;
    if (binaryCombinator == null) {
      if (other.binaryCombinator != null)
        return false;
    } else if (!binaryCombinator.equals(other.binaryCombinator))
      return false;
    if (binaryCombinatorKeyNum != other.binaryCombinatorKeyNum)
      return false;
    if (leftUnary == null) {
      if (other.leftUnary != null)
        return false;
    } else if (!leftUnary.equals(other.leftUnary))
      return false;
    if (leftUnaryKeyNum != other.leftUnaryKeyNum)
      return false;
    if (rightUnary == null) {
      if (other.rightUnary != null)
        return false;
    } else if (!rightUnary.equals(other.rightUnary))
      return false;
    if (rightUnaryKeyNum != other.rightUnaryKeyNum)
      return false;
    return true;
  }
}
