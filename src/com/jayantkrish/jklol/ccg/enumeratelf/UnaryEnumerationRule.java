package com.jayantkrish.jklol.ccg.enumeratelf;

import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.ccg.lambda.Type;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis;

public class UnaryEnumerationRule {

  private final Type argType;
  private final Expression2 ruleLf;
  private final ExpressionSimplifier simplifier;
  private final Map<String, String> typeDeclaration;
  
  public UnaryEnumerationRule(Type argType, Expression2 ruleLf, ExpressionSimplifier simplifier) {
    this.argType = Preconditions.checkNotNull(argType);
    this.ruleLf = Preconditions.checkNotNull(ruleLf);
    this.simplifier = Preconditions.checkNotNull(simplifier);
    this.typeDeclaration = Maps.newHashMap();
  }

  public boolean isApplicable(LfNode node) {
    return argType.equals(node.getType());
  }
  
  public LfNode apply(LfNode node) {
    Expression2 result = simplifier.apply(Expression2.nested(ruleLf, node.getLf()));
    Type resultType = StaticAnalysis.inferType(result, typeDeclaration);
    return new LfNode(result, resultType, node.getUsedMentions());
  }
}
