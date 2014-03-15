package com.jayantkrish.jklol.ccg.lambda;

import java.util.EmptyStackException;
import java.util.List;
import java.util.Stack;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.lisp.SExpression;

/**
 * A parser for LISP-style S-expressions. An example expression is:
 * 
 * <code>
 * (abc (def g) (h "i j k"))
 * </code>
 * 
 * @author jayantk
 */
public class ExpressionParser<T> {
  private final char openParen;
  private final char closeParen;
  private final char openQuote;
  private final char closeQuote;

  // Whether terms in the expression are separated by whitespace or 
  // alternateSeparator.
  private final boolean whitespaceSeparated;
  private final char alternateSeparator;

  private final T openParenExpression;
  private final T closeParenExpression;
  
  private final ExpressionFactory<T> factory;

  public static final char DEFAULT_OPEN_PAREN = '(';
  public static final char DEFAULT_CLOSE_PAREN = ')';
  public static final char DEFAULT_QUOTE = '"';
  public static final char DEFAULT_SEPARATOR = Character.MIN_VALUE;

  public ExpressionParser(char openParen, char closeParen, char openQuote, char closeQuote,
      boolean whitespaceSeparated, char alternateSeparator, ExpressionFactory<T> factory) {
    this.openParen = openParen;
    this.closeParen = closeParen;
    this.openQuote = openQuote;
    this.closeQuote = closeQuote;

    this.whitespaceSeparated = whitespaceSeparated;
    this.alternateSeparator = alternateSeparator;

    this.factory = factory;
    
    this.openParenExpression = factory.createTokenExpression(Character.toString(openParen));
    this.closeParenExpression = factory.createTokenExpression(Character.toString(closeParen));
  }

  public static ExpressionParser<Expression> lambdaCalculus() {
    return new ExpressionParser<Expression>(DEFAULT_OPEN_PAREN, DEFAULT_CLOSE_PAREN,
        DEFAULT_QUOTE, DEFAULT_QUOTE, true, DEFAULT_SEPARATOR, ExpressionFactories.getLambdaCalculusFactory());
  }

  public static ExpressionParser<TypedExpression> typedLambdaCalculus() {
    return new ExpressionParser<TypedExpression>(DEFAULT_OPEN_PAREN, DEFAULT_CLOSE_PAREN,
        DEFAULT_QUOTE, DEFAULT_QUOTE, true, DEFAULT_SEPARATOR, ExpressionFactories.getTypedLambdaCalculusFactory());
  }

  public static ExpressionParser<SExpression> sExpression() {
    return new ExpressionParser<SExpression>(DEFAULT_OPEN_PAREN, DEFAULT_CLOSE_PAREN,
        DEFAULT_QUOTE, DEFAULT_QUOTE, true, DEFAULT_SEPARATOR, ExpressionFactories.getSExpressionFactory());
  }

  public static ExpressionParser<Type> typeParser() {
    return new ExpressionParser<Type>('<', '>', DEFAULT_QUOTE, DEFAULT_QUOTE,
        false, ',', ExpressionFactories.getTypeFactory());
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
        if ((whitespaceSeparated && Character.isWhitespace(character)) ||
            (!whitespaceSeparated && character == alternateSeparator)) {
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

  public T parseSingleExpression(String expression) {
    return parseSingleExpression(tokenize(expression));
  }

  public List<T> parse(String expressions) {
    return parse(tokenize(expressions));
  }

  public T parseSingleExpression(List<String> tokenizedExpressionString) {
    List<T> expressions = parse(tokenizedExpressionString);
    Preconditions.checkState(expressions.size() == 1, "Illegal input string: " + tokenizedExpressionString);
    return expressions.get(0);
  }

  public List<T> parse(List<String> tokenizedExpressionString) {
    Stack<T> stack = new Stack<T>();
    try {
      for (String token : tokenizedExpressionString) {
        stack.push(factory.createTokenExpression(token));
        if (stack.peek().equals(closeParenExpression)) {
          stack.push(reduce(stack));
        }
      }
    } catch (EmptyStackException e) {
      throw new IllegalArgumentException("Invalid tokenized input: " + tokenizedExpressionString);
    }

    return stack;
  }

  private T reduce(Stack<T> stack) {
    // Pop the closing parenthesis
    Preconditions.checkArgument(stack.peek().equals(closeParenExpression));
    stack.pop();

    // Pop all arguments.
    Stack<T> arguments = new Stack<T>();
    while (!stack.peek().equals(openParenExpression)) {
      arguments.push(stack.pop());
    }

    // Pop the open parenthesis.
    stack.pop();

    // Add the parsed expression.
    List<T> subexpressions = Lists.newArrayList();
    for (T argument : Lists.reverse(arguments)) {
      subexpressions.add(argument);
    }

    return factory.createExpression(subexpressions);
  }
}