package com.jayantkrish.jklol.ccg.lambda;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class CommutativeExpression implements Expression {
  private static final long serialVersionUID = 1L;

  private final ConstantExpression function;
  private final List<Expression> arguments;
  
  public CommutativeExpression(ConstantExpression function, List<Expression> arguments) {
    this.function = Preconditions.checkNotNull(function);
    this.arguments = ImmutableList.copyOf(arguments);
  }
  
  public ConstantExpression getFunction() {
    return function;
  }
  
  public List<Expression> getArguments() {
    return arguments;
  }

  @Override
  public List<Expression> getSubexpressions() {
    List<Expression> subexpressions = Lists.newArrayList();
    subexpressions.add(function);
    subexpressions.addAll(arguments);
    return subexpressions;
  }

  @Override
  public Expression substitute(ConstantExpression constant, Expression replacement) {
    List<Expression> substituted = Lists.newArrayList();
    for (Expression subexpression : arguments) {
      substituted.add(subexpression.substitute(constant, replacement));
    }
    return new CommutativeExpression(function, substituted);
  }

  @Override
  public Expression simplify() {
    List<Expression> simplified = Lists.newArrayList();
    for (Expression subexpression : arguments) {
      simplified.add(subexpression.simplify());
    }
    
    List<Expression> resultClauses = Lists.newArrayList();
    for (Expression subexpression : simplified) {
      if (subexpression instanceof CommutativeExpression) {
        CommutativeExpression commutative = (CommutativeExpression) subexpression;
        if (commutative.getFunction().equals(getFunction())) {
          resultClauses.addAll(commutative.getArguments());
        } else {
          resultClauses.add(commutative);
        }
      } else {
        resultClauses.add(subexpression);
      }
    }

    return new CommutativeExpression(function, resultClauses);
  }
  
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("(");
    sb.append(getFunction());
    for (Expression argument : getArguments()) {
      sb.append(" ");
      sb.append(argument.toString());
    }
    sb.append(")");
    return sb.toString();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((arguments == null) ? 0 : arguments.hashCode());
    result = prime * result + ((function == null) ? 0 : function.hashCode());
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
    CommutativeExpression other = (CommutativeExpression) obj;
    if (arguments == null) {
      if (other.arguments != null)
        return false;
    } else if (!arguments.equals(other.arguments))
      return false;
    if (function == null) {
      if (other.function != null)
        return false;
    } else if (!function.equals(other.function))
      return false;
    return true;
  }
}
