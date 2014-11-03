package com.jayantkrish.jklol.lisp;

public enum ConstantValue {
  UNDEFINED, TRUE, FALSE, NIL;

  public boolean toBoolean() {
    if (this.equals(ConstantValue.TRUE)) {
      return true;
    } else {
      return false;
    }
  }
  
  public static ConstantValue fromBoolean(boolean value) {
    return value ? ConstantValue.TRUE : ConstantValue.FALSE;
  }
}
