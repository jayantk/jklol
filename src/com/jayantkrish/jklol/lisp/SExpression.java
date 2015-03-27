package com.jayantkrish.jklol.lisp;

import java.util.List;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/**
 * A LISP S-expression.
 * 
 * @author jayantk
 */
public class SExpression {

  // Null unless this expression is a constant.
  private final String constantName;
  // Unique index in the symbol table containing the string
  // constantName.
  private final int constantNameIndex;

  // If the constant is a primitive type (such as an integer
  // or double), this variable contains its value.
  private final Object primitiveValue;

  // Null unless this expression is not a constant. 
  private final List<SExpression> subexpressions;
  
  // Number of nodes in this expression's syntax tree
  private final int size;

  private SExpression(String constantName, int constantNameIndex, Object primitiveValue,
      List<SExpression> subexpressions, int size) {
    Preconditions.checkArgument(constantName == null ^ subexpressions == null);
    this.constantName = constantName;
    this.constantNameIndex = constantNameIndex;
    this.primitiveValue = primitiveValue;
    this.subexpressions = subexpressions;
    this.size = size;
  }

  public static SExpression constant(String constantName, int constantNameIndex,
      Object primitiveValue) {
    return new SExpression(constantName, constantNameIndex, primitiveValue, null, 1);
  }

  public static SExpression nested(List<SExpression> subexpressions) {
    int size = 1;
    for (SExpression subexpression : subexpressions) {
      size += subexpression.size();
    }

    return new SExpression(null, -1, null, subexpressions, size);
  }

  public boolean isConstant() {
    return constantName != null;
  }
  
  public String getConstant() {
    return constantName;
  }

  public int getConstantIndex() {
    return constantNameIndex;
  }

  public Object getConstantPrimitiveValue() {
    return primitiveValue;
  }

  public List<SExpression> getSubexpressions() {
    return subexpressions;
  }
  
  public int size() {
    return size;
  }
  
  /**
   * Gets the subexpression of {@code this} at position
   * {@code index} in the syntax tree.
   * 
   * @param index
   * @return
   */
  // TODO: rename this method, it's confusing to call it a
  // subexpression.
  public SExpression getSubexpression(int index) {
    Preconditions.checkArgument(index < size);
    if (index == 0) {
      return this;
    } else {
      int startIndex = 1;
      for (SExpression subexpression : subexpressions) {
        int endIndex = startIndex + subexpression.size();
        
        if (index < endIndex) {
          return subexpression.getSubexpression(index - startIndex);
        } else {
          startIndex += subexpression.size();
        }
      }
      // This should never happen due to the preconditions
      // check at the beginning.
      throw new IllegalArgumentException("Something bad happened.");
    }
  }

  public SExpression substitute(int index, SExpression newExpression) {
    Preconditions.checkArgument(index < size);
    if (index == 0) {
      return newExpression;
    } else {
      int startIndex = 1;
      for (int i = 0; i < subexpressions.size(); i++) {
        SExpression subexpression = subexpressions.get(i);
        int endIndex = startIndex + subexpression.size();
        
        if (index < endIndex) {
          SExpression substituted = subexpression.substitute(index - startIndex, newExpression);
          List<SExpression> newSubexpressions = Lists.newArrayList(subexpressions);
          newSubexpressions.set(i, substituted);
          return SExpression.nested(newSubexpressions);
        } else {
          startIndex += subexpression.size();
        }
      }
      // This should never happen due to the preconditions
      // check at the beginning.
      throw new IllegalArgumentException("Something bad happened.");
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
    result = prime * result
        + ((constantName == null) ? 0 : constantName.hashCode());
    result = prime * result
        + ((subexpressions == null) ? 0 : subexpressions.hashCode());
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
    SExpression other = (SExpression) obj;
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
}
