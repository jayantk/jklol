package com.jayantkrish.jklol.ccg.lambda;

import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
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
  
  public static ExpressionFactory<Expression2> getExpression2Factory() {
    return new Expression2Factory();
  }

  public static ExpressionFactory<Type> getTypeFactory() {
    return new ExpressionFactory<Type>() {
      public Type createTokenExpression(String token) {
        if (token.startsWith("#")) {
          int varNum = Integer.parseInt(token.substring(1));
          return Type.createTypeVariable(varNum);
        } else {
          return Type.createAtomic(token);
        }
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
      } else if (isDouble(internedString)) {
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
    
    private static boolean isDouble(String x) {
      try {
        Double.parseDouble(x);
        return true;
      } catch (NumberFormatException e) {
        return false;
      }
    }
  }

  private static class Expression2Factory implements ExpressionFactory<Expression2> {

    public Expression2Factory() {
    }

    public Expression2 createTokenExpression(String token) {
      return Expression2.constant(token);
    }

    public Expression2 createExpression(List<Expression2> subexpressions) {
      return Expression2.nested(subexpressions);
    }
  }


  private ExpressionFactories() {
    // Prevent instantiation.
  }
}
