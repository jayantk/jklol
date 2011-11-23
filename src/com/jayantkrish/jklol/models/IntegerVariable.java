package com.jayantkrish.jklol.models;

/**
 * A {@link Variable} that takes any integer value.
 * 
 * @author jayantk
 */
public class IntegerVariable implements Variable {

  @Override
  public Object getArbitraryValue() {
    return 0;
  }

  @Override
  public boolean canTakeValue(Object value) {
    return (value instanceof Integer);
  }
}
