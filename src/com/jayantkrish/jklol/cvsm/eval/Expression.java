package com.jayantkrish.jklol.cvsm.eval;

import java.util.List;

import com.google.common.base.Preconditions;

/**
 * A LISP S-expression.
 * 
 * @author jayantk
 */
public class Expression {
  
  // Null unless this expression is a constant.
  private final String constantName;
  // Null unless this expression is not a constant. 
  private final List<Expression> subexpressions;
  
  public Expression(String constantName, List<Expression> subexpressions) {
    Preconditions.checkArgument(constantName == null ^ subexpressions == null);
    this.constantName = constantName;
    this.subexpressions = subexpressions;
  }

  public boolean isConstant() {
    return constantName != null;
  }
  
  public String getConstant() {
    return constantName;
  }
  
  public List<Expression> getSubexpressions() {
    return subexpressions;
  }
}
