package com.jayantkrish.jklol.ccg.lambda2;

import java.util.List;

import com.google.common.collect.Lists;

/**
 * Transforms lambda calculus expressions into
 * continuation-passing style.
 * 
 * @author jayantk
 *
 */
public class CpsTransform {

  /**
   * Transform {@code exp} into continuation-passing style.
   * {@code continuation} is the global continuation that is
   * arranged to be called at the end of the returned expression's
   * evaluation. 
   * 
   * @param exp
   * @param continuation
   * @return
   */
  public static Expression2 apply(Expression2 exp, Expression2 continuation) {
    return T(exp, continuation);
  }

  private static Expression2 T(Expression2 exp, Expression2 continuation) {
    if (StaticAnalysis.isLambda(exp, 0)) {
      Expression2 cpsExp = M(exp);
      return Expression2.nested(continuation, cpsExp);
    } else if (exp.isConstant()) {
      Expression2 cpsExp = M(exp);
      return Expression2.nested(continuation, cpsExp);
    } else {
      List<Expression2> subexpressions = exp.getSubexpressions();
      List<String> newNames = StaticAnalysis.getNewVariableNames(exp, subexpressions.size());
      List<Expression2> newExprs = Expression2.constants(newNames);
      
      List<Expression2> app = Lists.newArrayList(newExprs);
      app.add(continuation);
      Expression2 result = Expression2.nested(app);
      for (int i = newNames.size() - 1; i >= 0; i--) {
        result = T(subexpressions.get(i), 
            Expression2.lambda(Lists.newArrayList(newNames.get(i)), result));
      }
      return result;
    }
  }
  
  private static Expression2 M(Expression2 expr) {
    if (StaticAnalysis.isLambda(expr, 0)) {
      String newVar = StaticAnalysis.getNewVariableName(expr);
      List<String> args = StaticAnalysis.getLambdaArguments(expr, 0);
      Expression2 body = StaticAnalysis.getLambdaBody(expr, 0);
      
      List<String> newArgs = Lists.newArrayList(args);
      newArgs.add(newVar);
      return Expression2.lambda(newArgs, T(body, Expression2.constant(newVar)));
    } else {
      return expr;
    }
  }

  private CpsTransform() {
    // Prevent instantiation.
  }
}
