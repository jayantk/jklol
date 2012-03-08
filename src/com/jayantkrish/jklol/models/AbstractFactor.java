package com.jayantkrish.jklol.models;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.FactorGraphProtos.FactorProto;
import com.jayantkrish.jklol.util.Assignment;

/**
 * AbstractFactor provides a partial implementation of Factor.
 * 
 * @author jayant
 * 
 */
public abstract class AbstractFactor implements Factor {

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
    Factor current = this;
    for (Factor other : others) {
      current = current.product(other);
    }
    return current;
  }
  
  @Override
  public Factor product(Factor... others) {
    return product(Arrays.asList(others));
  }
  
  /**
   * Initializes a protocol buffer builder for {@code this} with the
   * variables it contains. 
   */
  protected FactorProto.Builder getProtoBuilder() {
    FactorProto.Builder builder = FactorProto.newBuilder();
    builder.addAllVariableNum(getVars().getVariableNums());
    return builder;
  }

  /**
   * {@inheritDoc}
   * 
   * This default implementation always throws {@code CoercionError}.
   * Subclasses which support this operation should override this
   * implementation.
   */
  @Override
  public DiscreteFactor coerceToDiscrete() {
    throw new CoercionError("Cannot coerce this factor into a DiscreteFactor.");
  }
}
