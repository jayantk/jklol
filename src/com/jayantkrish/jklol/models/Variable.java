package com.jayantkrish.jklol.models;

/**
 * A random variable that takes on some set of Object values.
 * 
 * @author jayant
 */
public interface Variable {

  /**
   * Returns an identifier for this variable. The identifier is
   * expected to uniquely identify a type of variable, where a
   * variable is defined by the set of possible values it can take.
   * 
   * @return
   */
  public String getName();

  /**
   * Get an arbitrary value which can be assigned to this variable.
   * Useful for initializing things that don't care about the
   * particular value.
   */
  public Object getArbitraryValue();

  /**
   * Returns true if value can be legitimately assigned to this
   * variable.
   */
  public boolean canTakeValue(Object value);
}
