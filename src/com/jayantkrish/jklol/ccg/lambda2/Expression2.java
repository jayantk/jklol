package com.jayantkrish.jklol.ccg.lambda2;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;

public class Expression2 implements Serializable, Comparable<Expression2> {
  private static final long serialVersionUID = 1L;

  protected final String constantName;
  protected final List<Expression2> subexpressions;
  protected final int size;
  
  private Expression2(String constantName, List<Expression2> subexpressions, int size) {
    Preconditions.checkArgument(constantName == null || subexpressions == null);
    this.constantName = constantName;
    this.subexpressions = subexpressions;
    this.size = size;
  }

  public static Expression2 constant(String constantName) {
    return new Expression2(constantName, null, 1);
  }
  
  public static Expression2 stringValue(String value) {
    return new Expression2("\"" + value.replaceAll("\"", "\\\\\"") + "\"", null, 1);
  }

  public static List<Expression2> constants(List<String> constantNames) {
    List<Expression2> constants = Lists.newArrayList();
    for (String constantName : constantNames) {
      constants.add(Expression2.constant(constantName));
    }
    return constants;
  }
  
  public static List<Expression2> stringValues(List<String> values) {
    List<Expression2> constants = Lists.newArrayList();
    for (String value : values) {
      constants.add(Expression2.stringValue(value));
    }
    return constants;
  }

  public static Expression2 nested(Expression2... subexpressions) {
    return nested(Arrays.asList(subexpressions));
  }
  
  public static Expression2 nested(List<Expression2> subexpressions) {
    int size = 1;
    for (Expression2 subexpression : subexpressions) {
      size += subexpression.size();
    }

    return new Expression2(null, ImmutableList.copyOf(subexpressions), size);
  }
  
  public static Expression2 lambda(List<String> argNames, Expression2 body) {
    List<Expression2> lambdaBody = Lists.newArrayList();
    lambdaBody.add(Expression2.constant(StaticAnalysis.LAMBDA));
    lambdaBody.add(Expression2.nested(Expression2.constants(argNames)));
    lambdaBody.add(body);
    return Expression2.nested(lambdaBody);
  }

  public boolean isConstant() {
    return constantName != null;
  }

  public String getConstant() {
    return constantName;
  }

  public List<Expression2> getSubexpressions() {
    return subexpressions;
  }

  public int size() {
    return size;
  }

  private int[] findSubexpression(int index) {
    Preconditions.checkArgument(index < size, "Cannot get index %s of expression %s", index, this);
    int startIndex = 1;
    for (int i = 0; i < subexpressions.size(); i++) {
      Expression2 subexpression = subexpressions.get(i);
      int endIndex = startIndex + subexpression.size();
  
      if (index < endIndex) {
        return new int[] {i, index - startIndex, startIndex};
      } else {
        startIndex += subexpression.size();
      }
    }
    // This should never happen due to the preconditions
    // check at the beginning.
    throw new IllegalArgumentException("Something bad happened.");
  
  }

  /**
   * Gets the subexpression of {@code this} at position
   * {@code index} in the syntax tree. Index 0 always
   * returns {@code this}.
   * 
   * @param index
   * @return
   */
  public Expression2 getSubexpression(int index) {
    Preconditions.checkArgument(index < size);
    if (index == 0) {
      return this;
    } else {
      int[] parts = findSubexpression(index);
      return subexpressions.get(parts[0]).getSubexpression(parts[1]);
    }
  }
  
  /**
   * Get the index of the parent expression that contains the given index.
   * Returns -1 if the indexed expression has no parent.
   * 
   * @param index
   * @return
   */
  public int getParentExpressionIndex(int index) {
    if (index == 0) {
      return -1;
    } else {
      int[] parts = findSubexpression(index);
      int parentIndex = subexpressions.get(parts[0]).getParentExpressionIndex(parts[1]);
      
      if (parentIndex == -1) {
        // index refers to a child of this expression.
        return 0;
      } else {
        // Return the index into this expression of the chosen child,
        // plus the index into that expression.
        return parentIndex + parts[2]; 
      }
    }
  }
  
  /**
   * Gets an array of indexes to the children of the expression
   * given by {@code index}.
   * 
   * @param index
   * @return
   */
  public int[] getChildIndexes(int index) {
    if (index == 0) {
      if (isConstant()) {
        return new int[0];
      } else {
        int[] result = new int[subexpressions.size()];
        int startIndex = 1;
        for (int i = 0; i < subexpressions.size(); i++) {
          result[i] = startIndex;
          startIndex += subexpressions.get(i).size();
        }
        return result;
      }
    } else {
      int[] parts = findSubexpression(index);
      int[] result = subexpressions.get(parts[0]).getChildIndexes(parts[1]);
      for (int i = 0; i < result.length; i++) {
        result[i] += parts[2];
      }
      return result;
    }
  }

  /**
   * Gets the depth of the subexpression pointed to by {@code index}
   * in the tree. The root is at depth of 0, each of its children
   * at depth 1, etc.
   * 
   * @param index
   * @return
   */
  public int getDepth(int index) {
    if (index == 0) {
      return 0;
    } else {
      int[] parts = findSubexpression(index);
      return 1 + subexpressions.get(parts[0]).getDepth(parts[1]);
    }
  }

  /**
   * Replaces the expression at {@code index} in this expression
   * with {@code newConstantExpression} (as a constant expression).
   *   
   * @param index
   * @param newConstantExpression
   * @return
   */
  public Expression2 substitute(int index, String newConstantExpression) {
    return substitute(index, Expression2.constant(newConstantExpression));
  }

  /**
   * Replaces the expression at {@code index} in this expression 
   * with {@code newExpression}.
   * 
   * @param index
   * @param newExpression
   * @return
   */
  public Expression2 substitute(int index, Expression2 newExpression) {
    Preconditions.checkArgument(index < size);
    if (index == 0) {
      return newExpression;
    } else {
      int[] parts = findSubexpression(index);
      
      Expression2 substituted = subexpressions.get(parts[0]).substitute(parts[1], newExpression);
      List<Expression2> newSubexpressions = Lists.newArrayList(subexpressions);
      newSubexpressions.set(parts[0], substituted);
      return Expression2.nested(newSubexpressions);
    }
  }

  /**
   * Replaces all occurrences of {@code value} in this
   * expression with {@code replacement}. 
   * 
   * @param value
   * @param replacement
   * @return
   */
  public Expression2 substitute(String value, String replacement) {
    return substitute(Expression2.constant(value), Expression2.constant(replacement));
  }

  /**
   * Replaces all occurrences of {@code value} in this
   * expression with {@code replacement}. 
   * 
   * @param value
   * @param replacement
   * @return
   */
  public Expression2 substitute(String value, Expression2 replacement) {
    return substitute(Expression2.constant(value), replacement);
  }

  /**
   * Replaces all occurrences of {@code value} in this
   * expression with {@code replacement}. 
   * 
   * @param value
   * @param replacement
   * @return
   */
  public Expression2 substitute(Expression2 value, Expression2 replacement) {
    if (this.equals(value)) {
      return replacement;
    } else if (this.isConstant()) {
      return this;
    } else {
      List<Expression2> newSubexpressions = Lists.newArrayList();
      for (Expression2 sub : subexpressions) {
        newSubexpressions.add(sub.substitute(value, replacement));
      }
      return Expression2.nested(newSubexpressions);
    }
  }
  
  public Expression2 substituteInline(String value, List<Expression2> replacement) {
    if (this.isConstant()) {
      return this;
    } else {
      List<Expression2> newSubexpressions = Lists.newArrayList();
      for (Expression2 sub : subexpressions) {
        if (sub.isConstant() && sub.getConstant().equals(value)) {
          newSubexpressions.addAll(replacement);
        } else {
          newSubexpressions.add(sub.substituteInline(value, replacement));
        }
      }
      return Expression2.nested(newSubexpressions);
    }
  }
  
  /**
   * Returns {@code true} if this expression contains {@code subexpression}.
   * 
   * @param subexpression
   * @return
   */
  public boolean hasSubexpression(Expression2 subexpression) {
    return find(subexpression) >= 0;
  }
  
  /**
   * Returns the index of the first occurrence of {@code expression}
   * within {@code this} expression. Returns -1 if {@code expression}
   * is not found.
   * 
   * @param expression
   * @return
   */
  public int find(Expression2 expression) {
    return findHelper(expression, 0);
  }
  
  public int[] findAll(Expression2 expression) {
    List<Integer> indexes = Lists.newArrayList();
    
    for (int i = 0; i < size(); i++) {
      Expression2 sub = getSubexpression(i);
      if (sub.equals(expression)) {
        indexes.add(i);
      }
    }
    return Ints.toArray(indexes);
  }
  
  private int findHelper(Expression2 expression, int index) {
    if (this.equals(expression)) {
      return index;
    } else if (this.isConstant()) {
      return -1;
    } else {
      int[] childIndexes = getChildIndexes(0);
      for (int i = 0; i < subexpressions.size(); i++) {
        int subexpressionIndex = subexpressions.get(i).findHelper(expression, childIndexes[i]);
        if (subexpressionIndex >= 0) {
          return subexpressionIndex;
        }
      }
      return -1;
    }
  }

  @Override
  public String toString() {
    if (isConstant()) {
      return constantName;
    } else {
      return "(" + Joiner.on(" ").join(subexpressions) + ")";
    }
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((constantName == null) ? 0 : constantName.hashCode());
    result = prime * result + size;
    result = prime * result + ((subexpressions == null) ? 0 : subexpressions.hashCode());
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
    Expression2 other = (Expression2) obj;
    if (size != other.size)
      return false;
    if (constantName == null) {
      if (other.constantName != null)
        return false;
    } else if (!constantName.equals(other.constantName))
      return false;
    if (subexpressions == null) {
      if (other.subexpressions != null)
        return false;
    } else if (!subexpressions.equals(other.subexpressions))
      return false;
    return true;
  }

  @Override
  public int compareTo(Expression2 arg0) {
    return this.toString().compareTo(arg0.toString());
  }
}