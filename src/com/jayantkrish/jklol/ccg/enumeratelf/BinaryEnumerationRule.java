package com.jayantkrish.jklol.ccg.enumeratelf;

import com.jayantkrish.jklol.ccg.lambda.Type;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;

public class BinaryEnumerationRule {

  private final Type arg1Type;
  private final Type arg2Type;
  private final Type outputType;
  private final Expression2 ruleLf;
    
  public BinaryEnumerationRule(Type arg1Type, Type arg2Type, Type outputType, Expression2 ruleLf) {
    this.arg1Type = arg1Type;
    this.arg2Type = arg2Type;
    this.outputType = outputType;
    this.ruleLf = ruleLf;
  }

  public Expression2 getLogicalForm() {
    return ruleLf;
  }
  
  public Type getOutputType() {
    return outputType;
  }
  
  public boolean isTypeConsistent(Type t1, Type t2) {
    return arg1Type.equals(t1) && arg2Type.equals(t2);
  }

  public Expression2 apply(Expression2 arg1, Expression2 arg2, ExpressionSimplifier simplifier) {
    return simplifier.apply(Expression2.nested(ruleLf, arg1, arg2));
  }
}
