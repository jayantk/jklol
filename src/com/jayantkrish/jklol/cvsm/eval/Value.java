package com.jayantkrish.jklol.cvsm.eval;

import com.google.common.base.Preconditions;

public interface Value {
  public enum ConstantValue implements Value {
    UNDEFINED, TRUE, FALSE, NIL
  }

  public class StringValue implements Value {
    private String value;
    
    public StringValue(String value) {
      this.value = Preconditions.checkNotNull(value);
    }
    
    public String getValue() {
      return value;
    }
  }

  public class ConsValue implements Value {
    private Value car;
    private Value cdr;

    public ConsValue(Value car, Value cdr) {
      this.car = Preconditions.checkNotNull(car);
      this.cdr = Preconditions.checkNotNull(cdr);
    }

    public Value getCar() {
      return car;
    }

    /**
     * Returns {@code null} if this is the last element in
     * a list.
     * 
     * @return
     */
    public Value getCdr() {
      return cdr;
    }
  }
}
