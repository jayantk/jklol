package com.jayantkrish.jklol.lisp;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.util.IndexedList;

public class LispEval {
  
  private final IndexedList<String> symbolTable;

  public LispEval(IndexedList<String> symbolTable) {
    this.symbolTable = Preconditions.checkNotNull(symbolTable);
  }

  public IndexedList<String> getSymbolTable() {
    return symbolTable;
  }

  public EvalResult eval(SExpression expression, Environment environment) {
    if (expression.isConstant()) {
      // The expression may be a primitive type or a variable.
      String constantString = expression.getConstant();
      if (constantString.startsWith("\"") && constantString.endsWith("\"")) {
        String strippedQuotes = constantString.substring(1, constantString.length() - 1);
        return new EvalResult(strippedQuotes);
      } else if (constantString.matches("-?[0-9]+")) {
        // Integer primitive type
        int intValue = Integer.parseInt(constantString);
        return new EvalResult(intValue);
      } else {
        return new EvalResult(environment.getValue(constantString, symbolTable));
      }
    } else {
      List<SExpression> subexpressions = expression.getSubexpressions();
      SExpression first = subexpressions.get(0); 
      if (first.isConstant()) {
        String constantName = first.getConstant();

        // Check for syntactic special forms (define, lambda, etc.)
        if (constantName.equals("define")) {
          // Binds a name to a value in the environment.
          String nameToBind = subexpressions.get(1).getConstant();
          Object valueToBind = eval(subexpressions.get(2), environment).getValue();
          environment.bindName(nameToBind, valueToBind, symbolTable);
          return new EvalResult(ConstantValue.UNDEFINED);
        } else if (constantName.equals("begin")) {
          // Sequentially evaluates its subexpressions, chaining any 
          // environment changes.
          EvalResult result = new EvalResult(ConstantValue.UNDEFINED);
          for (int i = 1; i < subexpressions.size(); i++) {
            result = eval(subexpressions.get(i), environment);
          }
          return result;
        } else if (constantName.equals("lambda")) {
          // Create and return a function value representing this function.
          LispUtil.checkArgument(subexpressions.size() == 3);

          List<SExpression> argumentExpressions = subexpressions.get(1).getSubexpressions();
          int[] argumentNameIndexes = new int[argumentExpressions.size()];
          int ind = 0;
          for (SExpression argumentExpression : argumentExpressions) {
            LispUtil.checkArgument(argumentExpression.isConstant());
            argumentNameIndexes[ind] = argumentExpression.getConstantIndex();
            ind++;
          }

          SExpression functionBody = subexpressions.get(2); 
          return new EvalResult(new LambdaValue(argumentExpressions, argumentNameIndexes,
              functionBody, environment));
        } else if (constantName.equals("if")) {
          LispUtil.checkArgument(subexpressions.size() == 4);
          Object testCondition = eval(subexpressions.get(1), environment).getValue();
          if (ConstantValue.TRUE.equals(testCondition)) {
            return eval(subexpressions.get(2), environment);
          } else {
            return eval(subexpressions.get(3), environment);
          }
        }
      }

      // Default case: perform function application.
      List<Object> values = Lists.newArrayList();
      for (SExpression subexpression : subexpressions) {
        values.add(eval(subexpression, environment).getValue());
      }

      if (values.get(0) instanceof FunctionValue) {
        // Primitive procedures.
        FunctionValue functionToApply = (FunctionValue) values.get(0);
        List<Object> arguments = values.subList(1, values.size());
        Object result = functionToApply.apply(arguments, environment);
        return new EvalResult(result);
      } else if (values.get(0) instanceof LambdaValue) {
        // Lambda procedures.
        LambdaValue functionToApply = (LambdaValue) values.get(0);
        List<Object> arguments = values.subList(1, values.size());

        int[] argumentNameIndexes = functionToApply.getArgumentNameIndexes(); 
        LispUtil.checkArgument(argumentNameIndexes.length == arguments.size(),
            "Wrong number of arguments: expected %s, got %s",
            functionToApply.getArgumentExpressions(), arguments);

        Environment boundEnvironment = Environment.extend(functionToApply.getEnvironment());
        boundEnvironment.bindNames(argumentNameIndexes, arguments);

        return new EvalResult(eval(functionToApply.getBody(), boundEnvironment).getValue());
      } else {
        throw new IllegalArgumentException("Tried applying a non-function value: " + values.get(0));
      }
    }
  }

  public static Environment getDefaultEnvironment(IndexedList<String> symbolTable) {
    Environment env = Environment.empty();
    env.bindName("cons", new BuiltinFunctions.ConsFunction(), symbolTable);
    env.bindName("car", new BuiltinFunctions.CarFunction(), symbolTable);
    env.bindName("cdr", new BuiltinFunctions.CdrFunction(), symbolTable);
    env.bindName("list", new BuiltinFunctions.ListFunction(), symbolTable);
    env.bindName("nil?", new BuiltinFunctions.NilFunction(), symbolTable);
    env.bindName("+", new BuiltinFunctions.PlusFunction(), symbolTable);
    env.bindName("-", new BuiltinFunctions.MinusFunction(), symbolTable);
    env.bindName("=", new BuiltinFunctions.EqualsFunction(), symbolTable);
    env.bindName("not", new BuiltinFunctions.NotFunction(), symbolTable);
    env.bindName("and", new BuiltinFunctions.AndFunction(), symbolTable);
    env.bindName("or", new BuiltinFunctions.OrFunction(), symbolTable);
    env.bindName("lambda?", new BuiltinFunctions.IsLambda(), symbolTable);
    return env;
  }
  
  public static IndexedList<String> getInitialSymbolTable() {
    return IndexedList.create();
  }

  public static class EvalResult {
    private final Object value;

    public EvalResult(Object value) {
      this.value = Preconditions.checkNotNull(value);
    }

    public Object getValue() {
      return value;
    }
  }
}
