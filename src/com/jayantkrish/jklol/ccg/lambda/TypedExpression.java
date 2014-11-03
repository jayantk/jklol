package com.jayantkrish.jklol.ccg.lambda;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

public class TypedExpression {
  private final Expression expression;
  private final Type type;

  public TypedExpression(Expression expression, Type type) {
    this.expression = Preconditions.checkNotNull(expression);
    this.type = type;
  }

  public Expression getExpression() {
    return expression;
  }

  public Type getType() {
    return type;
  }

  public static List<Expression> getExpressions(List<TypedExpression> typedExpressions) {
    List<Expression> expressions = Lists.newArrayList();
    for (TypedExpression typedExpression : typedExpressions) {
      expressions.add(typedExpression.getExpression());
    }
    return expressions;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((expression == null) ? 0 : expression.hashCode());
    result = prime * result + ((type == null) ? 0 : type.hashCode());
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
    TypedExpression other = (TypedExpression) obj;
    if (expression == null) {
      if (other.expression != null)
        return false;
    } else if (!expression.equals(other.expression))
      return false;
    if (type == null) {
      if (other.type != null)
        return false;
    } else if (!type.equals(other.type))
      return false;
    return true;
  }
}
