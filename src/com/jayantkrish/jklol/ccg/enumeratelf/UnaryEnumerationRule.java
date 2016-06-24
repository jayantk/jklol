package com.jayantkrish.jklol.ccg.enumeratelf;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.lambda.Type;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;

public class UnaryEnumerationRule {

  private final Type argType;
  private final Type outputType;
  private final Expression2 ruleLf;
  
  public UnaryEnumerationRule(Type argType, Type outputType, Expression2 ruleLf) {
    this.argType = Preconditions.checkNotNull(argType);
    this.outputType = Preconditions.checkNotNull(outputType);
    this.ruleLf = Preconditions.checkNotNull(ruleLf);
  }
  
  public Type getInputType() {
    return argType;
  }

  public Type getOutputType() {
    return outputType;
  }
  
  public Expression2 getLogicalForm() {
    return ruleLf;
  }
  
  public boolean isTypeConsistent(Type t) {
    return argType.equals(t);
  }

  public Expression2 apply(Expression2 arg, ExpressionSimplifier simplifier) {
    return simplifier.apply(Expression2.nested(ruleLf, arg));
  }
}
