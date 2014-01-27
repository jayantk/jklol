package com.jayantkrish.jklol.lisp;

import com.google.common.base.Preconditions;

public interface Eval {
  EvalResult eval(SExpression expression, Environment environment);

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


