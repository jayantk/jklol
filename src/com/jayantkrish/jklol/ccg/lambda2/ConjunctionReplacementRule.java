package com.jayantkrish.jklol.ccg.lambda2;

import java.util.Collections;
import java.util.List;

import com.google.common.collect.Lists;

public class ConjunctionReplacementRule implements ExpressionReplacementRule {
  
  private final String conjunctionPred;
  
  public ConjunctionReplacementRule(String conjunctionPred) {
    this.conjunctionPred = conjunctionPred;
  }

  @Override
  public Expression2 getReplacement(Expression2 expression, int index) {
    Expression2 subexpression = expression.getSubexpression(index);
    if (isConjunction(subexpression)) {
      
      List<Expression2> conjuncts = Lists.newArrayList();
      int[] childIndexes = expression.getChildIndexes(index);
      for (int i = 1; i < childIndexes.length; i++) {
        // Check if any argument to the conjunction is also a conjunction
        Expression2 conjunct = expression.getSubexpression(childIndexes[i]);
        if (isConjunction(conjunct)) {
          List<Expression2> conjunctConjuncts = conjunct.getSubexpressions();
          conjuncts.addAll(conjunctConjuncts.subList(1, conjunctConjuncts.size()));
        } else {
          conjuncts.add(conjunct);
        }
      }
      Collections.sort(conjuncts);
      
      List<Expression2> originalConjuncts = subexpression.getSubexpressions();
      Expression2 func = originalConjuncts.get(0);
      originalConjuncts = originalConjuncts.subList(1, originalConjuncts.size());
      
      if (!originalConjuncts.equals(conjuncts)) {
        List<Expression2> newApplicationTerms = Lists.newArrayList(func);
        newApplicationTerms.addAll(conjuncts);
        return Expression2.nested(newApplicationTerms);
      }
    }
    return null;
  }
  
  private boolean isConjunction(Expression2 expression) {
    return !expression.isConstant() && expression.getSubexpression(1).isConstant()
        && expression.getSubexpression(1).getConstant().equals(conjunctionPred);
  }
}
