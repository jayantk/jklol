package com.jayantkrish.jklol.ccg.enumeratelf;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.lambda.Type;
import com.jayantkrish.jklol.ccg.lambda.TypeDeclaration;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis;

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

  public LfNode apply(LfNode arg1Node, LfNode arg2Node) {
    Expression2 result = simplifier.apply(Expression2.nested(ruleLf, arg1Node.getLf(), arg2Node.getLf()));
    Type resultType = StaticAnalysis.inferType(result, typeDeclaration);
    
    boolean[] usedMentions = new boolean[arg1Node.getUsedMentions().length];
    for (int i = 0; i < usedMentions.length; i++) {
      usedMentions[i] = arg1Node.getUsedMentions()[i] || arg2Node.getUsedMentions()[i];
    }

    return new LfNode(result, resultType, usedMentions);
  }
  
  public Expression2 apply(Expression2 arg1, Expression2 arg2) {
    return simplifier.apply(Expression2.nested(ruleLf, arg1, arg2));
  }
}
