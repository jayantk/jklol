package com.jayantkrish.jklol.cvsm.eval;

import java.util.List;

import com.google.common.base.Preconditions;

/**
 * A LISP S-expression.
 * 
 * @author jayantk
 */
public class SExpression {
  
  // Null unless this expression is a constant.
  private final String constantName;
  // Null unless this expression is not a constant. 
  private final List<SExpression> subexpressions;
  
  private SExpression(String constantName, List<SExpression> subexpressions) {
    Preconditions.checkArgument(constantName == null ^ subexpressions == null);
    this.constantName = constantName;
    this.subexpressions = subexpressions;
  }

  public static SExpression constant(String constantName) {
    return new SExpression(constantName, null);
  }

  public static SExpression nested(List<SExpression> subexpressions) {
    return new SExpression(null, subexpressions);
  }

  public boolean isConstant() {
    return constantName != null;
  }
  
  public String getConstant() {
    return constantName;
  }
  
  public List<SExpression> getSubexpressions() {
    return subexpressions;
  }
}
