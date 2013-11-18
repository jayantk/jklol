package com.jayantkrish.jklol.models.parametric;

import java.util.Iterator;

import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.util.AllAssignmentIterator;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Implementations of common {@link ParametricFactor} methods.
 * 
 * @author jayantk
 */
public abstract class AbstractParametricFactor implements ParametricFactor {

  private static final long serialVersionUID = -8462665505472931247L;
  
  private final VariableNumMap variables;
  
  public AbstractParametricFactor(VariableNumMap variables) {
    this.variables = variables;
  }
  
  @Override
  public VariableNumMap getVars() {
    return variables;
  }

  @Override
  public String getParameterDescription(SufficientStatistics statistics) {
    return getParameterDescription(statistics, -1);
  }

  @Override
  public void incrementSufficientStatisticsFromPartialAssignment(
      SufficientStatistics statistics, Assignment a, double count) {
    VariableNumMap notInAssignment = getVars().removeAll(a.getVariableNumsArray());
    Iterator<Assignment> iter = new AllAssignmentIterator(notInAssignment);
    while (iter.hasNext()) {
      incrementSufficientStatisticsFromAssignment(statistics, iter.next().union(a), count);
    }
  }
}
