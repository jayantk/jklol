package com.jayantkrish.jklol.inference;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.util.Assignment;

public abstract class AbstractMarginalSet implements MarginalSet {
  
  private final Assignment conditionedValues;
  
  public AbstractMarginalSet(Assignment conditionedValues) {
    this.conditionedValues = Preconditions.checkNotNull(conditionedValues);
  }

  @Override
  public Assignment getConditionedValues() {
    return conditionedValues;
  }
}
