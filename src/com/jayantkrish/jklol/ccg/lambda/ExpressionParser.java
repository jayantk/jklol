package com.jayantkrish.jklol.ccg.lambda;

import java.util.EmptyStackException;
import java.util.List;
import java.util.Stack;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/**
 * A parser for LISP-style S-expressions. An example expression is:
 * 
 * <code>
 * (abc (def g) (h "i j k"))
 * </code>
 * 
 * @author jayantk
 */
public class ExpressionParser {
  private final char openParen;
  private final char closeParen;
  private final char openQuote;
  private final char closeQuote;

  private final ConstantExpression openParenExpression;
  private final ConstantExpression closeParenExpression;
  
  private final ExpressionFactory factory;

  private static final char DEFAULT_OPEN_PAREN = '(';
  private static final char DEFAULT_CLOSE_PAREN = ')';
  private static final char DEFAULT_QUOTE = '"';

  /**
   * Gets an expression parser for lambda calculus.
   */
  public ExpressionParser() {
    this.openParen = DEFAULT_OPEN_PAREN;
    this.closeParen = DEFAULT_CLOSE_PAREN;
    this.openQuote = DEFAULT_QUOTE;
    this.closeQuote = DEFAULT_QUOTE;

    this.openParenExpression = new ConstantExpression(Character.toString(openParen));
    this.closeParenExpression = new ConstantExpression(Character.toString(closeParen));
    this.factory = ExpressionFactories.getLambdaCalculusFactory();
  }

  public ExpressionParser(char openParen, char closeParen, char openQuote, char closeQuote,
      ExpressionFactory factory) {
    this.openParen = openParen;
    this.closeParen = closeParen;
    this.openQuote = openQuote;
    this.closeQuote = closeQuote;

    this.openParenExpression = new ConstantExpression(Character.toString(openParen));
    this.closeParenExpression = new ConstantExpression(Character.toString(closeParen));
    
    this.factory = factory;
  }

  private List<String> tokenize(String expression) {
    boolean inQuotes = false;
    int exprStart = -1;
    List<String> tokens = Lists.newArrayList();
    for (int i = 0; i < expression.length(); i++) {
      char character = expression.charAt(i);

      boolean quoteOk = false;
      if (character == openQuote) {
        if (exprStart == -1 && !inQuotes) {
          inQuotes = true;
          exprStart = i;
          quoteOk = true;
        }
      } 
      if (!quoteOk && character == closeQuote) {
        if (exprStart != -1 && inQuotes) {
          inQuotes = false;
          quoteOk = true;
        } 
      }
      Preconditions.checkState((character != openQuote && character != closeQuote) || quoteOk,
          "Quoting error. Current: " + expression);

      if (!inQuotes) {
        if (Character.isWhitespace(character)) {
          if (exprStart != -1) {
            tokens.add(expression.substring(exprStart, i));
            exprStart = -1;
          }
        } else if (character == openParen || character == closeParen) {
          if (exprStart != -1) {
            tokens.add(expression.substring(exprStart, i));
          }
          tokens.add(expression.substring(i, i + 1));
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
        if (stack.peek().equals(closeParenExpression)) {
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
    Preconditions.checkArgument(stack.peek().equals(closeParenExpression));
    stack.pop();

    // Pop all arguments.
    Stack<Expression> arguments = new Stack<Expression>();
    while (!stack.peek().equals(openParenExpression)) {
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
      // This expression may be a special form.
      ConstantExpression constant = (ConstantExpression) subexpressions.get(0);
      return factory.createExpression(constant, subexpressions.subList(1, subexpressions.size()));
    }

    return new ApplicationExpression(subexpressions);
  }
}