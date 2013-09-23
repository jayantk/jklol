package com.jayantkrish.jklol.ccg.lambda;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.cvsm.eval.SExpression;

/**
 * Implementations of common {@code ExpressionFactory}s.
 * 
 * @author jayantk
 */
public class ExpressionFactories {

  /**
   * Gets a factory which builds an in-memory representation of the
   * expression tree, represented by {@link SExpressions}.
   * 
   * @return
   */
  public static ExpressionFactory<SExpression> getSExpressionFactory() {
    return new ExpressionFactory<SExpression>() {
      public SExpression createTokenExpression(String token) {
        return SExpression.constant(token);
      }
      
      public SExpression createExpression(List<SExpression> subexpressions) {
        return SExpression.nested(subexpressions);
      }
    };
  }

  /**
   * The default expression factory, which has no special forms. This
   * factory always returns an {@code ApplicationExpression}.
   * 
   * @return
   */
  public static ExpressionFactory<Expression> getDefaultFactory() {
    return new ExpressionFactory<Expression>() {
      public Expression createTokenExpression(String token) {
        return new ConstantExpression(token);
      }
      
      public Expression createExpression(List<Expression> subexpressions) {
        return new ApplicationExpression(subexpressions);
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
  public static ExpressionFactory<Expression> getLambdaCalculusFactory() {
    return new ExpressionFactory<Expression>() {
      public Expression createTokenExpression(String token) {
        return new ConstantExpression(token);
      }

      public Expression createExpression(List<Expression> subexpressions) {
        Preconditions.checkArgument(subexpressions.size() > 0);
        Expression firstTermExpression = subexpressions.get(0);
        List<Expression> remaining = subexpressions.subList(1, subexpressions.size());
        if (!(firstTermExpression instanceof ConstantExpression)) {
          // Special forms require the first term of the expression to be a constant.
          return new ApplicationExpression(subexpressions);
        }

        ConstantExpression firstTerm = (ConstantExpression) firstTermExpression;
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
