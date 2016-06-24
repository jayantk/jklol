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
  private final ExpressionSimplifier simplifier;
  
  private final TypeDeclaration typeDeclaration;
  
  public BinaryEnumerationRule(Type arg1Type, Type arg2Type, Expression2 ruleLf,
      ExpressionSimplifier simplifier, TypeDeclaration typeDeclaration) {
    this.arg1Type = arg1Type;
    this.arg2Type = arg2Type;
    this.ruleLf = ruleLf;
    this.simplifier = simplifier;
    this.typeDeclaration = Preconditions.checkNotNull(typeDeclaration);
    
    Type ruleFuncType = Type.createFunctional(arg1Type,
        Type.createFunctional(arg2Type, TypeDeclaration.TOP, false), false);
    ruleFuncType = StaticAnalysis.inferType(ruleLf, ruleFuncType, typeDeclaration);
    this.outputType = ruleFuncType.getReturnType().getReturnType();
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

  public boolean isApplicable(LfNode arg1Node, LfNode arg2Node) {
    boolean[] arg1Mentions = arg1Node.getUsedMentions();
    boolean[] arg2Mentions = arg2Node.getUsedMentions();
    for (int i = 0; i < arg1Mentions.length; i++) {
      if (arg1Mentions[i] && arg2Mentions[i]) {
        return false;
      }
    }

    return arg1Type.equals(arg1Node.getType()) && arg2Type.equals(arg2Node.getType());
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
