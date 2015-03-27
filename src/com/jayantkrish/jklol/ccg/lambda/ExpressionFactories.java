package com.jayantkrish.jklol.ccg.lambda;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.lisp.ConstantValue;
import com.jayantkrish.jklol.lisp.SExpression;
import com.jayantkrish.jklol.util.IndexedList;

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
  public static ExpressionFactory<SExpression> getSExpressionFactory(
      IndexedList<String> symbolTable) {
    return new SExpressionFactory(symbolTable);
  }

  public static ExpressionFactory<Type> getTypeFactory() {
    return new ExpressionFactory<Type>() {
      public Type createTokenExpression(String token) {
        return Type.createAtomic(token);
      }
      
      public Type createExpression(List<Type> types) {
        if (types.size() == 2) {
          return Type.createFunctional(types.get(0), types.get(1), false);
        } else if (types.size() == 3) {
          Preconditions.checkArgument(types.get(1).getAtomicTypeName().equals("*"));
          return Type.createFunctional(types.get(0), types.get(2), true);
        } else {
          throw new IllegalArgumentException("Invalid arguments: " + types);
        }
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

  public static ExpressionFactory<TypedExpression> getTypedLambdaCalculusFactory() {
    return new ExpressionFactory<TypedExpression>() {
      ExpressionParser<Type> typeParser = ExpressionParser.typeParser();

      public TypedExpression createTokenExpression(String token) {
        if (token.contains(":")) {
          String[] parts = token.split(":");
          Preconditions.checkArgument(parts.length == 2);
          return new TypedExpression(new ConstantExpression(parts[0]), typeParser.parseSingleExpression(parts[1]));
        } else {
          return new TypedExpression(new ConstantExpression(token), null);
        }
      }

      public TypedExpression createExpression(List<TypedExpression> typedSubexpressions) {
        Preconditions.checkArgument(typedSubexpressions.size() > 0);
        List<Expression> subexpressions = TypedExpression.getExpressions(typedSubexpressions);
        Expression firstTermExpression = subexpressions.get(0);
        if (!(firstTermExpression instanceof ConstantExpression)) {
          // Special forms require the first term of the expression to be a constant.
          return new TypedExpression(new ApplicationExpression(subexpressions), null);
        }

        ConstantExpression firstTerm = (ConstantExpression) firstTermExpression;
        String firstTermName = firstTerm.getName();
        List<TypedExpression> remaining = typedSubexpressions.subList(1, subexpressions.size());

        if (firstTermName.equals("lambda")) {
          // A lambda expression defines a function. Expected format
          // is (lambda x y z (body x y z))
          List<ConstantExpression> variables = Lists.newArrayList();
          List<Type> argTypes = Lists.newArrayList();
          for (int i = 0; i < remaining.size() - 1; i++) {
            variables.add((ConstantExpression) remaining.get(i).getExpression());
            Type argType = remaining.get(i).getType();
            Preconditions.checkArgument(argType != null, "%s", subexpressions);
            argTypes.add(argType);
          }
          Expression body = remaining.get(remaining.size() - 1).getExpression();
          return new TypedExpression(new LambdaExpression(variables, argTypes, body), null);
        } else if (firstTermName.equals("and")) {
          return new TypedExpression(new CommutativeOperator(firstTerm, TypedExpression.getExpressions(remaining)), null);
        } else {
          return new TypedExpression(new ApplicationExpression(subexpressions), null);
        }
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

  private static class SExpressionFactory implements ExpressionFactory<SExpression> {
    private final IndexedList<String> symbolTable;

    public SExpressionFactory(IndexedList<String> symbolTable) {
      this.symbolTable = Preconditions.checkNotNull(symbolTable);
    }

    public SExpression createTokenExpression(String token) {
      if (!symbolTable.contains(token)) {
        symbolTable.add(token);
      }
      int symbolIndex = symbolTable.getIndex(token);
      String internedString = symbolTable.get(symbolIndex);

      Object primitiveValue = null;
      if (internedString.startsWith("\"") && internedString.endsWith("\"")) {
        String strippedQuotes = internedString.substring(1, internedString.length() - 1);
        primitiveValue = strippedQuotes;
      } else if (internedString.matches("^-?[0-9]+$")) {
        // Integer primitive type
        primitiveValue = Integer.parseInt(internedString);
      } else if (internedString.matches("^-?[0-9]+\\.[0-9]*$")) {
        primitiveValue = Double.parseDouble(internedString);
      } else if (internedString.equals("#t")) {
        primitiveValue = ConstantValue.TRUE;
      } else if (internedString.equals("#f")) {
        primitiveValue = ConstantValue.FALSE;
      }

      return SExpression.constant(internedString, symbolIndex, primitiveValue);
    }

    public SExpression createExpression(List<SExpression> subexpressions) {
      return SExpression.nested(subexpressions);
    }
  }

  private ExpressionFactories() {
    // Prevent instantiation.
  }
}
