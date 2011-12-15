package com.jayantkrish.jklol.models;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.util.Assignment;

public class SetCoverFactor extends AbstractFactor {

  private final ImmutableSet<Object> requiredValues;
  private final ImmutableSet<Object> impossibleValues;
  
  private final List<Factor> inputVarFactors;
  private final List<Factor> cachedMaxMarginals;
  
  public SetCoverFactor(VariableNumMap inputVars, Set<Object> requiredValues, 
      Set<Object> impossibleValues, List<Factor> inputVarFactors) { 
    super(inputVars);
    Preconditions.checkArgument(inputVarFactors.size() == inputVars.size());
    this.requiredValues = ImmutableSet.copyOf(requiredValues);
    this.impossibleValues = ImmutableSet.copyOf(impossibleValues);
    
    // Ensure each argument factor is non-null 
    for (Factor factor : inputVarFactors) {
      Preconditions.checkArgument(factor != null);
    }
    this.inputVarFactors = Lists.newArrayList(inputVarFactors);
    
    // Precompute max-marginals.
    this.cachedMaxMarginals = cacheMaxMarginals();
  }
  
  private List<Factor> cacheMaxMarginals() {
    List<Factor> maxMarginals = Lists.newArrayListWithCapacity(inputVarFactors.size());
    for (int i = 0; i < inputVarFactors.size(); i++) {
      maxMarginals.add(null);
    }
    for (Object requiredValue : requiredValues) {
      // Fulfill each requirement by greedily selecting the factor with the best probability of each required value.
      double bestProbability = Double.NEGATIVE_INFINITY;
      int bestFactorIndex = -1;
      for (int i = 0; i < this.inputVarFactors.size(); i++) {
        Factor factor = inputVarFactors.get(i); 
        double prob = factor.getUnnormalizedProbability(requiredValue);
        
        if (prob >= bestProbability && maxMarginals.get(i) == null) {
          bestFactorIndex = i;
          bestProbability = prob;
        }
      }
      Preconditions.checkState(bestFactorIndex != -1);
      VariableNumMap factorVariables = inputVarFactors.get(bestFactorIndex).getVars();
      Factor maxMarginal = TableFactor.pointDistribution(factorVariables, 
          factorVariables.outcomeArrayToAssignment(requiredValue)).product(bestProbability);
      maxMarginals.set(bestFactorIndex, maxMarginal);
    }

    // For all unconstrained factors, the max-marginal is simply the factor with any illegal values
    // removed.
    for (int i = 0; i < inputVarFactors.size(); i++) {
      if (maxMarginals.get(i) == null) {
        DiscreteFactor currentFactor = inputVarFactors.get(i).coerceToDiscrete(); 
        TableFactorBuilder builder = new TableFactorBuilder(currentFactor.getVars());
        builder.incrementWeight(currentFactor);
        for (Object impossibleValue : impossibleValues) {
          builder.setWeight(0.0, impossibleValue);
        }
                
        maxMarginals.set(i, builder.build());
      }
    }
    return maxMarginals;
  }
  
  @Override
  public double getUnnormalizedProbability(Assignment assignment) {
    double logProbability = 0.0;
    List<Object> inputValues = assignment.intersection(getVars()).getValues();
    Set<Object> unfoundValues = Sets.newHashSet(requiredValues);
    for (int i = 0; i < inputValues.size(); i++) {
      Object inputValue = inputValues.get(i);
      if (impossibleValues.contains(inputValue)) {
        // If any value is in the set of impossible values, then this assignment
        // has zero
        // probability.
        return 0.0;
      }
      unfoundValues.remove(inputValue);
      if (inputVarFactors.get(i) != null) {
        logProbability += Math.log(inputVarFactors.get(i).getUnnormalizedProbability(assignment));
      }
    }

    if (unfoundValues.size() > 0) {
      // Not all of the required values were found in the inputVar.
      return 0.0;
    } else {
      return Math.exp(logProbability);
    }
  }

  @Override
  public Set<SeparatorSet> getComputableOutboundMessages(Map<SeparatorSet, Factor> inboundMessages) {
    for (Map.Entry<SeparatorSet, Factor> sepSet : inboundMessages.entrySet()) {
      if (sepSet.getValue() == null) {
        return Collections.emptySet();
      }
    }
    return inboundMessages.keySet();
  }

  @Override
  public Factor maxMarginalize(Collection<Integer> varNumsToEliminate) {
    VariableNumMap remainingVar = getVars().removeAll(varNumsToEliminate);
    Preconditions.checkArgument(remainingVar.size() == 1);
    
    for (int i = 0; i < cachedMaxMarginals.size(); i++) {
      if (cachedMaxMarginals.get(i).getVars().containsAll(remainingVar)) {
        return cachedMaxMarginals.get(i);
      }
    }
    // We should always find a factor containing the variable, and therefore
    // never reach this point.
    throw new IllegalStateException("Could not find a cached max marginal for: " + remainingVar);
  }
  
  @Override
  public Factor product(Factor other) {
    Preconditions.checkArgument(other.getVars().size() == 1);
    Preconditions.checkArgument(other.getVars().containsAny(getVars()));

    int otherVarIndex = other.getVars().getVariableNums().get(0);
    int listIndex = getVars().getVariableNums().indexOf(otherVarIndex);

    List<Factor> newInputVarFactors = Lists.<Factor>newArrayList(inputVarFactors);
    if (newInputVarFactors.get(listIndex) == null) {
      newInputVarFactors.set(listIndex, other);
    } else {
      newInputVarFactors.set(listIndex, newInputVarFactors.get(listIndex).product(other));
    }

    return new SetCoverFactor(getVars(), requiredValues, 
        impossibleValues, newInputVarFactors);
  }
  
  @Override
  public double size() {
    return Double.POSITIVE_INFINITY;
  }

  @Override
  public Factor relabelVariables(VariableRelabeling relabeling) {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public Factor conditional(Assignment assignment) {
    if (!getVars().containsAny(assignment.getVariableNums())) {
      return this;
    }
    VariableNumMap myVars = getVars();    
    Set<Object> newRequiredValues = Sets.newHashSet(requiredValues);
    for (Integer varNum : assignment.getVariableNums()) {
      if (myVars.contains(varNum)) {
        Object value = assignment.getValue(varNum);
        newRequiredValues.remove(value);
        
        if (impossibleValues.contains(value)) {
          // Can't possibly satisfy the constraints anymore.
          return TableFactor.zero(myVars.removeAll(assignment.getVariableNums()));
        }
      }
    }
    
    List<Factor> newFactors = Lists.newArrayListWithCapacity(inputVarFactors.size()); 
    for (Factor inputVarFactor : inputVarFactors) {
      if (!inputVarFactor.getVars().containsAny(assignment.getVariableNums())) {
        newFactors.add(inputVarFactor);
      }
    }
    
    return new SetCoverFactor(myVars.removeAll(assignment.getVariableNums()), 
        newRequiredValues, impossibleValues, newFactors);
  }

  @Override
  public Factor marginalize(Collection<Integer> varNumsToEliminate) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Factor add(Factor other) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Factor maximum(Factor other) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Factor product(double constant) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Factor inverse() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Assignment sample() {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<Assignment> getMostLikelyAssignments(int numAssignments) {
    Preconditions.checkArgument(numAssignments == 1);
    List<Assignment> outputAssignments = Lists.newArrayListWithCapacity(cachedMaxMarginals.size());
    for (Factor cached : cachedMaxMarginals) {
      outputAssignments.addAll(cached.getMostLikelyAssignments(numAssignments));
    }
    return Arrays.asList(Assignment.unionAll(outputAssignments));
  }
}
