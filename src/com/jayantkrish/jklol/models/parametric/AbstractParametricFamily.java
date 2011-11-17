package com.jayantkrish.jklol.models.parametric;

import java.util.Collection;

import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.VariableNumMap;


/**
 * Implementations of common {@link ParametricFamily} methods.
 * 
 * @author jayantk
 * @param <T>
 */
public abstract class AbstractParametricFamily<T>
  implements ParametricFamily<T> {

  private final FactorGraph baseFactorGraph;
  
  public AbstractParametricFamily(FactorGraph baseFactorGraph) {
    this.baseFactorGraph = baseFactorGraph;
  }
  
  @Override
  public VariableNumMap getVariables() {
    return baseFactorGraph.getVariables();
  }
  
  @Override
  public VariableNumMap lookupVariables(Collection<String> names) {
    return baseFactorGraph.lookupVariables(names);
  }
    
  protected FactorGraph getBaseFactorGraph() {
    return baseFactorGraph;
  }
}
