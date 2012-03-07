package com.jayantkrish.jklol.models;

import java.util.Map;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.util.Assignment;

public class FunctionWeightedRelation implements WeightedRelation {
  private final Function<Object, Object> function;

  public FunctionWeightedRelation(Function<Object, Object> function) {
    this.function = function;
  }

  @Override
  public double getWeight(Object domainValue, Object rangeValue) {
    if (function.apply(domainValue).equals(rangeValue)) {
      return 1.0;
    }
    return 0.0;
  }

  private DiscreteObjectFactor getJointDistribution(Factor domainFactor, 
      Factor rangeFactor) {
    VariableNumMap rangeVariable = rangeFactor.getVars();
    DiscreteObjectFactor domainAsObjectFactor = (DiscreteObjectFactor) domainFactor;
    Map<Assignment, Double> jointProbabilities = Maps.newHashMap();
    for (Assignment domainValue : domainAsObjectFactor.assignments()) {
      Object rangeValue = function.apply(domainValue.getOnlyValue());
      
      Assignment rangeAssignment = rangeVariable.outcomeArrayToAssignment(rangeValue);
      Assignment jointAssignment = domainValue.union(rangeAssignment);
      jointProbabilities.put(jointAssignment, domainFactor.getUnnormalizedProbability(domainValue)
          * rangeFactor.getUnnormalizedProbability(rangeAssignment));
    }

    return new DiscreteObjectFactor(domainFactor.getVars().union(rangeVariable),
        jointProbabilities);
  }

  @Override
  public Factor computeRangeMarginal(Factor domainFactor, Factor rangeFactor) {
    Preconditions.checkNotNull(domainFactor);
    Preconditions.checkNotNull(rangeFactor);
    return getJointDistribution(domainFactor, rangeFactor)
        .marginalize(domainFactor.getVars());
  }

  @Override
  public Factor computeRangeMaxMarginal(Factor domainFactor, Factor rangeFactor) {
    Preconditions.checkNotNull(domainFactor);
    Preconditions.checkNotNull(rangeFactor);
    return getJointDistribution(domainFactor, rangeFactor)
        .maxMarginalize(domainFactor.getVars());
  }

  @Override
  public Factor computeDomainMarginal(Factor domainFactor, Factor rangeFactor) {
    Preconditions.checkNotNull(domainFactor);
    Preconditions.checkNotNull(rangeFactor);
    return getJointDistribution(domainFactor, rangeFactor)
        .marginalize(rangeFactor.getVars());
  }

  @Override
  public Factor computeDomainMaxMarginal(Factor domainFactor, Factor rangeFactor) {
    Preconditions.checkNotNull(domainFactor);
    Preconditions.checkNotNull(rangeFactor);
    return getJointDistribution(domainFactor, rangeFactor)
        .maxMarginalize(rangeFactor.getVars());
  }
}