package com.jayantkrish.jklol.inference;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Common implementations of some {@code MarginalSet} methods.
 *
 * @author jayantk
 */
public abstract class AbstractMarginalSet implements MarginalSet {

  // Marginal sets are defined over two types of variables. marginalVariables have
  // a factor defined over them, while conditionedVariables are fixed to a value by an assignment. 
  private final VariableNumMap marginalVariables;
  private final VariableNumMap conditionedVariables;
  private final Assignment conditionedValues;
  
  public AbstractMarginalSet(VariableNumMap marginalVariables, 
      VariableNumMap conditionedVariables, Assignment conditionedValues) {
    Preconditions.checkArgument(marginalVariables.union(conditionedVariables)
        .containsAll(conditionedValues.getVariableNums()));
    this.marginalVariables = marginalVariables;
    this.conditionedVariables = conditionedVariables;
    this.conditionedValues = conditionedValues;
  }
  
  @Override 
  public VariableNumMap getVariables() {
    return marginalVariables.union(conditionedVariables);
  }

  @Override
  public Assignment getConditionedValues() {
    return conditionedValues;
  }
  
  protected VariableNumMap getMarginalVariables() {
    return marginalVariables;
  }
  
  protected VariableNumMap getConditionedVariables() {
    return conditionedVariables;
  }
}
