package com.jayantkrish.jklol.cvsm.eval;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

public class LispEval implements Eval {

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
        return new EvalResult(environment.getValue(constantString));
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
          environment.bindName(nameToBind, valueToBind);
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
          Preconditions.checkArgument(subexpressions.size() == 3);

          List<String> argumentNames = Lists.newArrayList();
          List<SExpression> argumentExpressions = subexpressions.get(1).getSubexpressions();
          for (SExpression argumentExpression : argumentExpressions) {
            Preconditions.checkArgument(argumentExpression.isConstant());
            argumentNames.add(argumentExpression.getConstant());
          }

          SExpression functionBody = subexpressions.get(2); 
          return new EvalResult(new LambdaValue(argumentNames, functionBody, environment));
        } else if (constantName.equals("if")) {
          Preconditions.checkArgument(subexpressions.size() == 4);
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

      FunctionValue functionToApply = (FunctionValue) values.get(0);
      List<Object> arguments = values.subList(1, values.size());
      Object result = functionToApply.apply(arguments, environment, this);
      return new EvalResult(result);
    }
  }

  public static Environment getDefaultEnvironment() {
    Environment env = Environment.empty();
    env.bindName("cons", new BuiltinFunctions.ConsFunction());
    env.bindName("car", new BuiltinFunctions.CarFunction());
    env.bindName("cdr", new BuiltinFunctions.CdrFunction());
    env.bindName("list", new BuiltinFunctions.ListFunction());
    env.bindName("nil?", new BuiltinFunctions.NilFunction());
    env.bindName("+", new BuiltinFunctions.PlusFunction());
    env.bindName("-", new BuiltinFunctions.MinusFunction());
    env.bindName("=", new BuiltinFunctions.EqualsFunction());
    env.bindName("not", new BuiltinFunctions.NotFunction());
    env.bindName("and", new BuiltinFunctions.AndFunction());
    env.bindName("or", new BuiltinFunctions.OrFunction());
    return env;
  }
}
