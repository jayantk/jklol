package com.jayantkrish.jklol.models;

import com.jayantkrish.jklol.util.Assignment;

/**
 * A factory for constructing factors of a certain type.
 * 
 * @author jayantk
 */
public interface FactorFactory {

  /**
   * Creates a point distribution on {@code variables}, which assigns
   * unnormalized probability 1.0 to {@code assignment} and 0 probability to
   * everything else.
   * 
   * @param variables
   * @param assignment
   * @return
   */
  public Factor pointDistribution(VariableNumMap variables, Assignment assignment);
}
