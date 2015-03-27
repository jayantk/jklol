package com.jayantkrish.jklol.lisp.syntax;

import java.util.List;

import com.jayantkrish.jklol.lisp.SExpression;

public class LambdaApplicationReplacementRule implements SExpressionReplacementRule {

  @Override
  public SExpression replace(SExpression input) {
    if (!input.isConstant() && input.getSubexpressions().size() > 0) {
      
      List<SExpression> subexpressions = input.getSubexpressions();
      SExpression first = subexpressions.get(0);
      
      if (!first.isConstant() && first.getSubexpressions().size() > 0) {
        
        
      }
      
    }
  }
}
