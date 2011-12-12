package com.jayantkrish.jklol.models.parametric;

import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.dynamic.DynamicVariableSet;


/**
 * Implementations of common {@link ParametricFamily} methods.
 * 
 * @author jayantk
 * @param <T>
 */
public abstract class AbstractParametricFamily<T>
  implements ParametricFamily<T> {

  private final DynamicFactorGraph baseFactorGraph;
  
  public AbstractParametricFamily(DynamicFactorGraph baseFactorGraph) {
    this.baseFactorGraph = baseFactorGraph;
  }
  
  @Override
  public DynamicVariableSet getVariables() {
    return baseFactorGraph.getVariables();
  }
  
  protected DynamicFactorGraph getBaseFactorGraph() {
    return baseFactorGraph;
  }
}
