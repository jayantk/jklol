package com.jayantkrish.jklol.ccg.enumeratelf;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.lambda.Type;
import com.jayantkrish.jklol.ccg.lambda.TypeDeclaration;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis;

public class UnaryEnumerationRule {

  private final Type argType;
  private final Expression2 ruleLf;
  private final ExpressionSimplifier simplifier;
  private final TypeDeclaration typeDeclaration;
  
  public UnaryEnumerationRule(Type argType, Expression2 ruleLf, ExpressionSimplifier simplifier,
      TypeDeclaration typeDeclaration) {
    this.argType = Preconditions.checkNotNull(argType);
    this.ruleLf = Preconditions.checkNotNull(ruleLf);
    this.simplifier = Preconditions.checkNotNull(simplifier);
    this.typeDeclaration = Preconditions.checkNotNull(typeDeclaration);
  }

  public boolean isApplicable(LfNode node) {
    // TODO: should be isUnifiable
    return argType.equals(node.getType());
  }
  
  public LfNode apply(LfNode node) {
    Expression2 result = simplifier.apply(Expression2.nested(ruleLf, node.getLf()));
    Type resultType = StaticAnalysis.inferType(result, typeDeclaration);
    return new LfNode(result, resultType, node.getUsedMentions());
  }
}
