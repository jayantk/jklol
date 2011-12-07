package com.jayantkrish.jklol.models;

/**
 * A {@link Variable} which can take any value of a given type.
 *  
 * @author jayantk
 */
public class ObjectVariable implements Variable {
  
  private final Class<?> type;
  
  public ObjectVariable(Class<?> type) {
    this.type = type;
  }

  @Override
  public Object getArbitraryValue() {
    return null;
  }

  @Override
  public boolean canTakeValue(Object value) {
    return type.isInstance(value);
  }
  
  public Class<?> getObjectType() {
    return type;
  }
}
