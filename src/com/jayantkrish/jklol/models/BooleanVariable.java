package com.jayantkrish.jklol.models;



public class BooleanVariable implements Variable {

  @Override
  public Object getArbitraryValue() {
    return false;
  }

  @Override
  public boolean canTakeValue(Object value) {
    return value instanceof Boolean;
  }
  
  @Override
  public String toString() {
    return "BooleanVariable";
  }
}
