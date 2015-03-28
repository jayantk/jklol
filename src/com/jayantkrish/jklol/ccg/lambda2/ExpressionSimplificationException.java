package com.jayantkrish.jklol.ccg.lambda2;

/**
 * Exception thrown when an expression cannot be simplified
 * because it is syntactically-malformed.
 * 
 * @author jayant
 *
 */
public class ExpressionSimplificationException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public ExpressionSimplificationException() {
    super();
  }

  public ExpressionSimplificationException(String arg0, Throwable arg1) {
    super(arg0, arg1);
  }

  public ExpressionSimplificationException(String arg0) {
    super(arg0);
  }

  public ExpressionSimplificationException(Throwable arg0) {
    super(arg0);
  }
}
