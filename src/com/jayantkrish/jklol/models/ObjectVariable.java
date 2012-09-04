package com.jayantkrish.jklol.models;

import java.io.Serializable;

/**
 * A {@link Variable} which can take any value of a given type.
 *  
 * @author jayantk
 */
public class ObjectVariable implements Variable, Serializable {
  
  private static final long serialVersionUID = 2881212128913134802L;
  
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
  
  @Override
  public String toString() {
    return "ObjectVariable(" + type.toString() + ")";
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((type == null) ? 0 : type.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (!getClass().toString().equals(obj.getClass().toString()))
      return false;
    ObjectVariable other = (ObjectVariable) obj;
    if (type == null) {
      if (other.type != null)
        return false;
    } else if (!type.equals(other.type))
      return false;
    return true;
  }
}
