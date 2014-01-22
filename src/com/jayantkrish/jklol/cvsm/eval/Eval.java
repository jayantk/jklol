package com.jayantkrish.jklol.cvsm.eval;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

public class Eval {

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
        // Check for syntactic primitives (define, lambda, etc.)
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
          return new EvalResult(new FunctionValue(argumentNames, functionBody, environment));
        } else if (constantName.equals("cons")) {
          Preconditions.checkArgument(subexpressions.size() == 3);

          Object car = eval(subexpressions.get(1), environment).getValue();
          Object cdr = eval(subexpressions.get(2), environment).getValue();

          return new EvalResult(new ConsValue(car, cdr));
        } else if (constantName.equals("car")) {
          Preconditions.checkArgument(subexpressions.size() == 2);
          ConsValue value = (ConsValue) eval(subexpressions.get(1), environment).getValue();
          return new EvalResult(value.getCar());
        } else if (constantName.equals("cdr")) {
          Preconditions.checkArgument(subexpressions.size() == 2);
          ConsValue value = (ConsValue) eval(subexpressions.get(1), environment).getValue();
          return new EvalResult(value.getCdr());
        } else if (constantName.equals("list")) {
          int numExpressions = subexpressions.size();
          Object listValue = ConstantValue.NIL;
          for (int i = numExpressions - 1; i > 0; i--) {
            Object value = eval(subexpressions.get(i), environment).getValue();
            listValue = new ConsValue(value, listValue);
          }
          return new EvalResult(listValue);
        } else if (constantName.equals("nil?")) {
          Preconditions.checkArgument(subexpressions.size() == 2);
          Object value = eval(subexpressions.get(1), environment).getValue();
          
          Object result = null;
          if (ConstantValue.NIL.equals(value)) {
            result = ConstantValue.TRUE;
          } else {
            result = ConstantValue.FALSE;
          }
          return new EvalResult(result);
        } else if (constantName.equals("if")) {
          Preconditions.checkArgument(subexpressions.size() == 4);
          Object testCondition = eval(subexpressions.get(1), environment).getValue();
          if (ConstantValue.TRUE.equals(testCondition)) {
            return eval(subexpressions.get(2), environment);
          } else {
            return eval(subexpressions.get(3), environment);
          }
        } else if (constantName.equals("+")) {
          int resultValue = 0;
          for (int i = 1; i < subexpressions.size(); i++) {
            Integer subexpressionValue = (Integer) eval(subexpressions.get(i), environment).getValue();
            resultValue += subexpressionValue;
          }
          return new EvalResult(resultValue);
        } else if (constantName.equals("-")) {
          int resultValue = 0;
          for (int i = 1; i < subexpressions.size(); i++) {
            Integer subexpressionValue = (Integer) eval(subexpressions.get(i), environment).getValue();
            if (i == 1) {
              resultValue += subexpressionValue;
            } else {
              resultValue -= subexpressionValue;
            }
          }
          return new EvalResult(resultValue);
        }
      }

      // Default case: perform function application.
      List<Object> values = Lists.newArrayList();
      for (SExpression subexpression : subexpressions) {
        values.add(eval(subexpression, environment).getValue());
      }

      FunctionValue functionToApply = (FunctionValue) values.get(0);
      List<Object> arguments = values.subList(1, values.size());
      Object result = functionToApply.apply(arguments, this);
      return new EvalResult(result);
    }
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
