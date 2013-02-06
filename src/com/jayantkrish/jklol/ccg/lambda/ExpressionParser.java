package com.jayantkrish.jklol.ccg.lambda;

import java.util.Arrays;
import java.util.List;
import java.util.Stack;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

public class ExpressionParser {
  
  private static final ConstantExpression OPEN_PAREN = new ConstantExpression("(");
  private static final ConstantExpression CLOSE_PAREN = new ConstantExpression(")");

  public ExpressionParser() {}

  public Expression parseSingleExpression(String expression) {
    String transformedExpression = expression.replaceAll("([()])", " $1 ");
    List<String> tokens = Arrays.asList(transformedExpression.trim().split("\\s+"));
    return parseSingleExpression(tokens);
  }

  public Expression parseSingleExpression(List<String> tokenizedExpressionString) {
    List<Expression> expressions = parse(tokenizedExpressionString);
    Preconditions.checkState(expressions.size() == 1, "Illegal input string: " + tokenizedExpressionString);
    return expressions.get(0);
  }
  
  public List<Expression> parse(List<String> tokenizedExpressionString) {
    Stack<Expression> stack = new Stack<Expression>();
    
    for (String token : tokenizedExpressionString) {
      stack.push(new ConstantExpression(token));
      if (stack.peek().equals(CLOSE_PAREN)) {
        stack.push(reduce(stack));
      }
    }
    
    return stack;
  }
  
  private Expression reduce(Stack<Expression> stack) {
    // Pop the closing parenthesis
    Preconditions.checkArgument(stack.peek().equals(CLOSE_PAREN));
    stack.pop();
    
    // Pop all arguments.
    Stack<Expression> arguments = new Stack<Expression>();
    while (!stack.peek().equals(OPEN_PAREN)) {
      arguments.push(stack.pop());
    }
    
    // Pop the open parenthesis.
    stack.pop();
    
    // Add the parsed expression.
    List<Expression> subexpressions = Lists.newArrayList();
    for (Expression argument : Lists.reverse(arguments)) {
      subexpressions.add(argument);
    }
    
    if (subexpressions.size() > 0 && subexpressions.get(0) instanceof ConstantExpression) {
      ConstantExpression constant = (ConstantExpression) subexpressions.get(0);
      if (constant.getName().equals("lambda")) {
        List<ConstantExpression> variables = Lists.newArrayList();
        for (int i = 1; i < subexpressions.size() - 1; i++) {
          variables.add((ConstantExpression) subexpressions.get(i));
        }
        Expression body = subexpressions.get(subexpressions.size() - 1);
        return new LambdaExpression(variables, body);
      } else if (constant.getName().equals("and")) {
        return new CommutativeExpression(constant, subexpressions.subList(1, subexpressions.size()));
      }
    }
    return new ApplicationExpression(subexpressions);
  }
}
