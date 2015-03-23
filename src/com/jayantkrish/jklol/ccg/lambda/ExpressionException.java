package com.jayantkrish.jklol.ccg.lambda;

/**
 * Exception thrown by {@code Expression} instances when
 * an operation, such as simplification, fails due to a 
 * problem in the expression, such as a function being applied
 * to too many arguments. This exception may be caught if
 * such malformed expressions are expected.
 * 
 * @author jayant
 *
 */
public class ExpressionException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ExpressionException() {
    super();
  }

  public ExpressionException(String arg0, Throwable arg1) {
    super(arg0, arg1);
  }

  public ExpressionException(String arg0) {
    super(arg0);
  }

  public ExpressionException(Throwable arg0) {
    super(arg0);
  }
}
