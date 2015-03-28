package com.jayantkrish.jklol.ccg.lambda2;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * Simplification engine for expressions. Repeatedly applies
 * a set of local replacement rules until a fixed point is
 * reached. The replacement rules can be configured to
 * perform, e.g., beta reduction of lambda expressions.
 * Other application-specific semantics-preserving
 * transformations can also be applied using additional
 * rules.
 * 
 * @author jayant
 * 
 */
public class ExpressionSimplifier {
  
  private final List<ExpressionReplacementRule> rules;

  public ExpressionSimplifier(List<ExpressionReplacementRule> rules) {
    this.rules = ImmutableList.copyOf(rules);
  }
  
  /**
   * Default simplifier for lambda calculus expressions. This
   * simplifier performs beta reduction of lambda expressions
   * and canonicalizes variable names. 
   * 
   * @return
   */
  public static ExpressionSimplifier lambdaCalculus() {
    List<ExpressionReplacementRule> rules = Lists.newArrayList();
    rules.add(new LambdaApplicationReplacementRule());
    rules.add(new VariableCanonicalizationReplacementRule());
    return new ExpressionSimplifier(rules);
  }

  public Expression2 apply(Expression2 expression) {
    boolean changed = true;
    while (changed) {
      // Iterate backward to not have to worry about the
      // expression getting shorter on each pass.
      changed = false;
      for (ExpressionReplacementRule rule : rules) {
        for (int i = expression.size() - 1; i >= 0; i--) {
          Expression2 result = rule.getReplacement(expression, i);
          if (result != null) {
            System.out.println(result + " "+ expression);
            expression = expression.substitute(i, result);
            changed = true;
          }
        }
      }
    }
    return expression;
  }
}
