package com.jayantkrish.jklol.ccg.lambda;

import java.util.EmptyStackException;
import java.util.List;
import java.util.Stack;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class ExpressionParser {
  
  private static final ConstantExpression OPEN_PAREN = new ConstantExpression("(");
  private static final ConstantExpression CLOSE_PAREN = new ConstantExpression(")");

  public ExpressionParser() {}
  
  private List<String> tokenize(String expression) {
    boolean inQuotes = false;
    int exprStart = -1;
    List<String> tokens = Lists.newArrayList();
    for (int i = 0; i < expression.length(); i++) {
      char character = expression.charAt(i);
      
      if (character == '\"') {
        if (exprStart == -1 && !inQuotes) {
          inQuotes = true;
          exprStart = i;
        } else if (exprStart != -1 && inQuotes) {
          inQuotes = false;
        } else {
          Preconditions.checkState(false, "Quoting error. Current: " + expression);
        }
      }

      if (!inQuotes) {
        if (Character.isWhitespace(character)) {
          if (exprStart != -1) {
            tokens.add(expression.substring(exprStart, i));
            exprStart = -1;
          }
        } else if (character == '(' || character == ')') {
          if (exprStart != -1) {
            tokens.add(expression.substring(exprStart, i));
          }
          tokens.add(expression.substring(i, i+1));
          exprStart = -1;
        } else if (exprStart == -1) {
          // A meaningful, non whitespace, non parenthesis character.
          exprStart = i;
        }
      }
    }
    if (exprStart != -1) {
      tokens.add(expression.substring(exprStart, expression.length()));
    }
    return tokens;
    /*
    String transformedExpression = expression.replaceAll("([()])", " $1 ");
    return Arrays.asList(transformedExpression.trim().split("\\s+"));
    */
  }

  public Expression parseSingleExpression(String expression) {
    return parseSingleExpression(tokenize(expression));
  }
  
  public List<Expression> parse(String expressions) {
    return parse(tokenize(expressions));
  }

  public Expression parseSingleExpression(List<String> tokenizedExpressionString) {
    List<Expression> expressions = parse(tokenizedExpressionString);
    Preconditions.checkState(expressions.size() == 1, "Illegal input string: " + tokenizedExpressionString);
    return expressions.get(0);
  }
  
  public List<Expression> parse(List<String> tokenizedExpressionString) {
    Stack<Expression> stack = new Stack<Expression>();
    try {
      for (String token : tokenizedExpressionString) {
        stack.push(new ConstantExpression(token));
        if (stack.peek().equals(CLOSE_PAREN)) {
          stack.push(reduce(stack));
        }
      }
    } catch (EmptyStackException e) {
      throw new IllegalArgumentException("Invalid tokenized input: " + tokenizedExpressionString);
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
      String constantName = constant.getName();
      if (constantName.equals("lambda")) {
        List<ConstantExpression> variables = Lists.newArrayList();
        for (int i = 1; i < subexpressions.size() - 1; i++) {
          variables.add((ConstantExpression) subexpressions.get(i));
        }
        Expression body = subexpressions.get(subexpressions.size() - 1);
        return new LambdaExpression(variables, body);
      } else if (constantName.equals("and")) {
        return new CommutativeOperator(constant, subexpressions.subList(1, subexpressions.size()));
      } else if (constantName.equals("set")) {
        return new CommutativeOperator(constant, subexpressions.subList(1, subexpressions.size()));
      } else if (constantName.equals("exists")) {
        List<ConstantExpression> variables = Lists.newArrayList();
        for (int i = 1; i < subexpressions.size() - 1; i++) {
          variables.add((ConstantExpression) subexpressions.get(i));
        }
        Expression body = subexpressions.get(subexpressions.size() - 1);
        return new QuantifierExpression(constantName, variables, body);
      } else if (constantName.equals("forall")) {
        List<ConstantExpression> variables = Lists.newArrayList();
        List<Expression> values = Lists.newArrayList();
        for (int i = 1; i < subexpressions.size() - 1; i++) {
          ApplicationExpression app = (ApplicationExpression) subexpressions.get(i);
          variables.add((ConstantExpression) app.getFunction());
          values.add(Iterables.getOnlyElement(app.getArguments()));
        }
        Expression body = subexpressions.get(subexpressions.size() - 1);
        return new ForAllExpression(variables, values, body);
      }
    }
    return new ApplicationExpression(subexpressions);
  }
}