package com.jayantkrish.jklol.ccg.lambda;

import java.util.List;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * Implementations of common {@code ExpressionFactory}s.
 * 
 * @author jayantk
 */
public class ExpressionFactories {

  /**
   * The default expression factory, which has no special forms. This
   * factory always returns an {@code ApplicationExpression}.
   * 
   * @return
   */
  public static ExpressionFactory getDefaultFactory() {
    return new ExpressionFactory() {
      public Expression createExpression(ConstantExpression firstTerm, List<Expression> remaining) {
        return new ApplicationExpression(firstTerm, remaining);
      }
    };
  }

  /**
   * An expression factory which understands a subset of lambda
   * calculus. Allowed special forms are:
   * 
   * <code>
   * (lambda x y z (body x y z))
   * (and expr1 expr2 ...)
   * (set expr1 expr2 ...)
   * (exists x y z (body))
   * (forall (p (set ...)) body)
   * </code>
   * 
   * @return
   */
  public static ExpressionFactory getLambdaCalculusFactory() {
    return new ExpressionFactory() {
      public Expression createExpression(ConstantExpression firstTerm, List<Expression> remaining) {
        String firstTermName = firstTerm.getName();

        if (firstTermName.equals("lambda")) {
          // A lambda expression defines a function. Expected format
          // is (lambda x y z (body x y z))
          List<ConstantExpression> variables = Lists.newArrayList();
          for (int i = 0; i < remaining.size() - 1; i++) {
            variables.add((ConstantExpression) remaining.get(i));
          }
          Expression body = remaining.get(remaining.size() - 1);
          return new LambdaExpression(variables, body);
        } else if (firstTermName.equals("and")) {
          return new CommutativeOperator(firstTerm, remaining);
        } else if (firstTermName.equals("set")) {
          return new CommutativeOperator(firstTerm, remaining);
        } else if (firstTermName.equals("exists")) {
          List<ConstantExpression> variables = Lists.newArrayList();
          for (int i = 0; i < remaining.size() - 1; i++) {
            variables.add((ConstantExpression) remaining.get(i));
          }
          Expression body = remaining.get(remaining.size() - 1);
          return new QuantifierExpression(firstTerm.getName(), variables, body);
        } else if (firstTermName.equals("forall")) {
          List<ConstantExpression> variables = Lists.newArrayList();
          List<Expression> values = Lists.newArrayList();
          for (int i = 0; i < remaining.size() - 1; i++) {
            ApplicationExpression app = (ApplicationExpression) remaining.get(i);
            variables.add((ConstantExpression) app.getFunction());
            values.add(Iterables.getOnlyElement(app.getArguments()));
          }
          Expression body = remaining.get(remaining.size() - 1);
          return new ForAllExpression(variables, values, body);
        } else {
          return new ApplicationExpression(firstTerm, remaining);
        }
      }
    };
  }

  private ExpressionFactories() {
    // Prevent instantiation.
  }
}
