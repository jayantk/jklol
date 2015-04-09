package com.jayantkrish.jklol.ccg.lambda2;

import java.util.List;

import com.google.common.collect.Lists;

/**
 * Replacement rule that applies beta reduction to lambda
 * expressions. For example, this rule replaces expressions
 * of the form ((lambda x (foo x)) arg) with (foo arg).
 *  
 * @author jayant
 *
 */
public class LambdaApplicationReplacementRule implements ExpressionReplacementRule {

  @Override
  public Expression2 getReplacement(Expression2 expression, int index) {
    Expression2 subexpression = expression.getSubexpression(index);
    if (!subexpression.isConstant() && StaticAnalysis.isLambda(subexpression, 1)) {
      List<Expression2> applicationTerms = subexpression.getSubexpressions();
      Expression2 lambdaExpression = applicationTerms.get(0);
      List<Expression2> applicationArgs = applicationTerms.subList(1, applicationTerms.size());
      
      List<String> lambdaArgs = StaticAnalysis.getLambdaArguments(lambdaExpression, 0);
      Expression2 body = StaticAnalysis.getLambdaBody(lambdaExpression, 0);

      if (applicationArgs.size() > lambdaArgs.size()) {
        throw new ExpressionSimplificationException("Lambda applied to too many arguments: " + subexpression);
      }

      for (int i = 0; i < applicationArgs.size(); i++) {
        int[] freeIndexes = StaticAnalysis.getIndexesOfFreeVariable(body, lambdaArgs.get(i));
        for (int j = freeIndexes.length - 1; j >= 0; j--) {
          // Do substitutions in backward order to prevent the indexes
          // from changing as the body is modified.
          body = body.substitute(freeIndexes[j], applicationArgs.get(i));
        }
      }

      if (applicationArgs.size() < lambdaArgs.size()) {
        List<Expression2> terms = Lists.newArrayList();
        terms.add(Expression2.constant("lambda"));
        terms.addAll(Expression2.constants(lambdaArgs.subList(applicationArgs.size(), lambdaArgs.size())));
        terms.add(body);
        return Expression2.nested(terms);
      } else {
        return body;
      }
    }
    return null;
  }
}
