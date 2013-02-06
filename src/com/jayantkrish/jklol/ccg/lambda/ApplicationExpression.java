package com.jayantkrish.jklol.ccg.lambda;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

public class ApplicationExpression extends AbstractExpression {
  private static final long serialVersionUID = 1L;
  
  public ApplicationExpression(List<Expression> subexpressions) {
    super(subexpressions);
    Preconditions.checkArgument(subexpressions.size() >= 1);
  }
  
  public Expression getFunction() {
    return getSubexpressions().get(0);
  }
  
  public List<Expression> getArguments() {
    List<Expression> subexpressions = getSubexpressions();
    return subexpressions.subList(1, subexpressions.size());
  }
  
  @Override
  public Expression substitute(ConstantExpression constant, Expression replacement) {
    List<Expression> subexpressions = getSubexpressions();
    List<Expression> substituted = Lists.newArrayList();
    for (Expression subexpression : subexpressions) {
      substituted.add(subexpression.substitute(constant, replacement));
    }

    return new ApplicationExpression(substituted);
  }
  
  @Override
  public Expression simplify() {
    // First simplify all arguments
    List<Expression> simplifiedArguments = Lists.newArrayList();
    List<Expression> arguments = getArguments();
    for (Expression argument : arguments) {
      simplifiedArguments.add(argument.simplify());
    }
    
    Expression function = getFunction().simplify();
    if (function instanceof LambdaExpression) {
      LambdaExpression lambdaFunction = (LambdaExpression) function;
      return lambdaFunction.reduce(simplifiedArguments).simplify();
    } else {
      List<Expression> subexpressions = Lists.newArrayList(function);
      subexpressions.addAll(simplifiedArguments);
      return new ApplicationExpression(subexpressions);
    }
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
    result = prime * result + ((getArguments() == null) ? 0 : getArguments().hashCode());
    result = prime * result + ((getFunction() == null) ? 0 : getFunction().hashCode());
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
    ApplicationExpression other = (ApplicationExpression) obj;
    if (getArguments() == null) {
      if (other.getArguments() != null)
        return false;
    } else if (!getArguments().equals(other.getArguments()))
      return false;
    if (getFunction() == null) {
      if (other.getFunction() != null)
        return false;
    } else if (!getFunction().equals(other.getFunction()))
      return false;
    return true;
  }
}
