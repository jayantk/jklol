package com.jayantkrish.jklol.ccg.lambda;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class LambdaExpression implements Expression {
  private static final long serialVersionUID = 1L;

  private final List<ConstantExpression> argumentVariables;
  private final Expression body;
  
  public LambdaExpression(List<ConstantExpression> argumentVariables, Expression body) {
    this.argumentVariables = ImmutableList.copyOf(argumentVariables);
    this.body = Preconditions.checkNotNull(body);
  }
    
  public Expression reduce(List<Expression> argumentValues) {
    Preconditions.checkArgument(argumentValues.size() == argumentVariables.size());
    Expression substitutedBody = body;
    for (int i = 0; i < argumentVariables.size(); i++) {      
      substitutedBody = substitutedBody.substitute(argumentVariables.get(i),
          argumentValues.get(i));
    }
    return substitutedBody;
  }

  @Override
  public List<Expression> getSubexpressions() {
    List<Expression> subexpressions = Lists.newArrayList();
    subexpressions.addAll(argumentVariables);
    subexpressions.add(body);
    return subexpressions;
  }

  @Override
  public Expression substitute(ConstantExpression constant, Expression replacement) {
    Expression substitution = body.substitute(constant, replacement);
    return new LambdaExpression(argumentVariables, substitution);
  }
  
  @Override
  public Expression simplify() {
    Expression simplifiedBody = body.simplify();
    return new LambdaExpression(argumentVariables, simplifiedBody);
  }
  
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("(lambda");
    for (Expression argument : argumentVariables) {
      sb.append(" ");
      sb.append(argument.toString());
    }
    
    sb.append(" ");
    sb.append(body.toString());
    sb.append(")");
    return sb.toString();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((argumentVariables == null) ? 0 : argumentVariables.hashCode());
    result = prime * result + ((body == null) ? 0 : body.hashCode());
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
    LambdaExpression other = (LambdaExpression) obj;
    if (argumentVariables == null) {
      if (other.argumentVariables != null)
        return false;
    } else if (!argumentVariables.equals(other.argumentVariables))
      return false;
    if (body == null) {
      if (other.body != null)
        return false;
    } else if (!body.equals(other.body))
      return false;
    return true;
  }
}
