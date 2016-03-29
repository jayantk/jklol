package com.jayantkrish.jklol.lisp;

public class EvalError extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public EvalError() {
    super();
  }

  public EvalError(String message, Throwable cause, boolean enableSuppression,
      boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }

  public EvalError(String message, Throwable cause) {
    super(message, cause);
  }

  public EvalError(String message) {
    super(message);
  }

  public EvalError(Throwable cause) {
    super(cause);
  }
}
