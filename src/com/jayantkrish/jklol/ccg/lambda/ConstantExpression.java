package com.jayantkrish.jklol.ccg.lambda;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

public class ConstantExpression extends AbstractExpression implements Comparable<ConstantExpression> {
  private static final long serialVersionUID = 1L;
  
  private final String name;
  private final Type type;
  
  public ConstantExpression(String name) {
    this.name = Preconditions.checkNotNull(name);
    this.type = null;
  }
  
  public ConstantExpression(String name, Type type) {
    this.name = Preconditions.checkNotNull(name);
    this.type = type;
  }

  public static ConstantExpression generateUniqueVariable() {
    // TODO: just use a counter. (worried about serialization)
    int random = (int) (Math.random() * 1000000.0);
    return new ConstantExpression("var" + random);
  }
  
  public static List<ConstantExpression> generateUniqueVariables(int num) {
    List<ConstantExpression> vars = Lists.newArrayList();
    for (int i = 0; i< num; i++) {
      vars.add(generateUniqueVariable());
    }
    return vars;
  }

  public String getName() {
    return name;
  }
  
  @Override
  public void getFreeVariables(Set<ConstantExpression> accumulator) {
    accumulator.add(this);
  }
  
  @Override
  public void getBoundVariables(Set<ConstantExpression> accumulator) {
    // No bound variables.
  }
  
  @Override
  public List<ConstantExpression> getLocallyBoundVariables() {
    return Collections.emptyList();
  }
  
  @Override
  public ConstantExpression renameVariable(ConstantExpression variable, ConstantExpression replacement) {
    if (this.equals(variable)) {
      return replacement;
    } else {
      return this;
    }
  }

  @Override
  public Expression substitute(ConstantExpression constant, Expression replacement) {
    if (this.equals(constant)) {
      return replacement;
    } else {
      return this;
    }
  }

  @Override
  public Expression simplify() {
    return this;
  }
  
  @Override
  public boolean functionallyEquals(Expression other) {
    if (other instanceof ConstantExpression) {
      return ((ConstantExpression) other).name.equals(name);
    }
    return false;
  }

  @Override
  public Type getType(TypeContext context) {
    if (type == null) {
      return context.getTypeForName(name);
    } else {
      return type;
    }
  }

  @Override
  public String toString() {
    if (type == null) {
      return name;
    } else {
      return name + ":" + type;
    }
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((name == null) ? 0 : name.hashCode());
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
    ConstantExpression other = (ConstantExpression) obj;
    if (name == null) {
      if (other.name != null)
        return false;
    } else if (!name.equals(other.name))
      return false;
    if (type == null) {
      if (other.type != null)
        return false;
    } else if (!type.equals(other.type))
      return false;
    return true;
  }

  @Override
  public int compareTo(ConstantExpression other) {
    return name.compareTo(other.name);
  }
}
