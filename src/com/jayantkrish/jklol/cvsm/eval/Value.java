package com.jayantkrish.jklol.cvsm.eval;

public interface Value {
  public enum ConstantValue implements Value {
    UNDEFINED, TRUE, FALSE
  };
}
