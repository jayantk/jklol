package com.jayantkrish.jklol.models;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.util.Assignment;

/**
 * {@code AbstractFactor} provides a partial implementation of {@code Factor}.
 * 
 * @author jayant
 * 
 */
public abstract class AbstractFactor implements Factor, Serializable {

  private static final long serialVersionUID = -5261363608555889520L;
  
  private VariableNumMap vars;

  public AbstractFactor(VariableNumMap vars) {
    Preconditions.checkNotNull(vars);
    this.vars = vars;
  }

  @Override
  public VariableNumMap getVars() {
    return vars;
  }

  @Override
  public double getUnnormalizedProbability(List<? extends Object> outcome) {
    Preconditions.checkNotNull(outcome);
    Preconditions.checkArgument(outcome.size() == getVars().size());

    Assignment a = getVars().outcomeToAssignment(outcome);
    return getUnnormalizedProbability(a);
  }

  @Override
  public double getUnnormalizedProbability(Object... outcome) {
    return getUnnormalizedProbability(Arrays.asList(outcome));
  }

  @Override
  public double getUnnormalizedLogProbability(List<? extends Object> outcome) {
    Preconditions.checkNotNull(outcome);
    Preconditions.checkArgument(outcome.size() == getVars().size());

    Assignment a = getVars().outcomeToAssignment(outcome);
    return getUnnormalizedLogProbability(a);
  }

  @Override
  public double getUnnormalizedLogProbability(Object... outcome) {
    return getUnnormalizedLogProbability(Arrays.asList(outcome));
  }
  
  @Override
  public double getTotalUnnormalizedProbability() {
    return marginalize(getVars()).getUnnormalizedProbability(Assignment.EMPTY);
  }

  @Override
  public double getTotalUnnormalizedLogProbability() {
    return Math.log(getTotalUnnormalizedProbability());
  }

  @Override
  public Factor marginalize(Integer... varNums) {
    return marginalize(Arrays.asList(varNums));
  }

  @Override
  public Factor marginalize(VariableNumMap vars) {
    return marginalize(vars.getVariableNums());
  }

  @Override
  public Factor maxMarginalize(Integer... varNums) {
    return maxMarginalize(Arrays.asList(varNums));
  }

  @Override
  public Factor maxMarginalize(VariableNumMap vars) {
    return maxMarginalize(vars.getVariableNums());
  }

  @Override
  public Factor add(List<Factor> others) {
    Preconditions.checkNotNull(others);
    Factor current = this;
    for (Factor other : others) {
      Preconditions.checkArgument(other.getVars().equals(current.getVars()));
      current = current.add(other);
    }
    return current;
  }

  @Override
  public Factor maximum(List<Factor> others) {
    Preconditions.checkNotNull(others);
    Factor current = this;
    for (Factor other : others) {
      Preconditions.checkArgument(other.getVars().equals(current.getVars()));
      current = current.maximum(other);
    }
    return current;
  }

  @Override
  public Factor product(List<Factor> others) {
    return product(others.toArray(new Factor[others.size()]));
  }

  @Override
  public Factor product(Factor... others) {
    Factor current = this;
    for (Factor other : others) {
      current = current.product(other);
    }
    return current;
  }

  /**
   * {@inheritDoc}
   * 
   * This default implementation always throws {@code CoercionError}. Subclasses
   * which support this operation should override this implementation.
   */
  @Override
  public DiscreteFactor coerceToDiscrete() {
    throw new CoercionError("Cannot coerce this factor into a DiscreteFactor.");
  }

  @Override
  public String getParameterDescription() {
    // This implementation will probably not be detailed enough.
    // Subclasses should override this method with a
    // correct implementation.
    return toString();
  }
}
