package com.jayantkrish.jklol.ccg.lambda2;

import java.util.List;

import com.google.common.base.Preconditions;

public class ExistsReplacementRule implements ExpressionReplacementRule {

  private final String existsPred;
  private final String conjunctionPred;
  private final String equalPred;

  public ExistsReplacementRule(String existsPred, String conjunctionPred, String equalPred) {
    this.existsPred = Preconditions.checkNotNull(existsPred);
    this.conjunctionPred = Preconditions.checkNotNull(conjunctionPred);
    this.equalPred = Preconditions.checkNotNull(equalPred);
  }

  @Override
  public Expression2 getReplacement(Expression2 expression, int index) {
    Expression2 subexpression = expression.getSubexpression(index);
    if (isExists(subexpression)) {
      int[] childIndexes = expression.getChildIndexes(index);
      Preconditions.checkState(childIndexes.length == 2,
          "Ill-formed existential quantifier expression: %s", subexpression);
      
      Expression2 lambdaPart = expression.getSubexpression(childIndexes[1]);
      Preconditions.checkState(StaticAnalysis.isLambda(lambdaPart), 
          "Ill-formed existential quantifier expression: %s", subexpression);
      
      List<String> args = StaticAnalysis.getLambdaArguments(lambdaPart);
      Preconditions.checkState(args.size() == 1, 
          "Ill-formed existential quantifier expression: %s", subexpression);
      String arg = args.get(0);
      
      Expression2 lambdaBody = StaticAnalysis.getLambdaBody(lambdaPart);
      
      // We can only simplify expressions of the form
      // (exists (lambda (x) (and (= x foobar) ...))).
      // Determine if the lambda part of the quantifier satisfies 
      // these conditions.
      if (lambdaBody.isConstant()) {
        return null;
      }
      
      List<Expression2> conjuncts = lambdaBody.getSubexpressions();
      Expression2 func = conjuncts.get(0);
      if (!func.isConstant() || !func.getConstant().equals(conjunctionPred)) {
        return null;
      }
      
      // Determine if one of the elements of the conjunction is an equals
      // binding of a variable.
      Expression2 binding = null;
      for (int i = 1; i < conjuncts.size(); i++) {
        Expression2 conjunct = conjuncts.get(i);
        if (!conjunct.isConstant()) {
          List<Expression2> subexpressions = conjunct.getSubexpressions();
          Expression2 first = subexpressions.get(0);
          if (first.isConstant() && first.getConstant().equals(equalPred)) {
            Preconditions.checkState(subexpressions.size() == 3);
            
            Expression2 second = subexpressions.get(1);
            Expression2 third = subexpressions.get(2);
            
            boolean secondIsArg = second.isConstant() && second.getConstant().equals(arg);
            boolean thirdIsArg = third.isConstant() && third.getConstant().equals(arg);
            
            if (secondIsArg && !thirdIsArg) {
              binding = third;
              break;
            } else if (thirdIsArg && !secondIsArg) {
              binding = second;
              break;
            }
          }
        }
      }
      
      // Can only simplify the expression if we find a binding for the variable.
      if (binding == null) {
        return null;
      }
      
      // Binding is the only value for which the lambda could possibly return
      // true. Replace the quantifier with the lambda applied to this value.
      return Expression2.nested(lambdaPart, binding); 
    }
    return null;
  }
  
  private boolean isExists(Expression2 expression) {
    if (!expression.isConstant()) {
      List<Expression2> subexpressions = expression.getSubexpressions();
      if (subexpressions.size() > 0) {
        Expression2 first = subexpressions.get(0);
        if (first.isConstant() && first.getConstant().equals(existsPred)) {
          return true;
        }
      }
    }
    return false;
  }
}
