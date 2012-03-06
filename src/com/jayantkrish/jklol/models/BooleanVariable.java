package com.jayantkrish.jklol.models;

import com.jayantkrish.jklol.models.FactorGraphProtos.VariableProto;

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
  
  @Override
	public VariableProto toProto() {
    throw new UnsupportedOperationException("Not yet implemented.");
  }
}
