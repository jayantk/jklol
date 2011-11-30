package com.jayantkrish.jklol.inference;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Stores a set of {@link Factor}s representing marginal distributions and uses
 * them to answer queries for marginals.
 * 
 * @author jayant
 * 
 */
public class FactorMarginalSet extends AbstractMarginalSet {

  private final ImmutableList<Factor> allFactors;
  private final Multimap<Integer, Factor> variableFactorMap;
  private final double partitionFunction;

  /**
   * Constructs a {@code FactorMarginalSet} conditioned on
   * {@code conditionedVars} taking {@code conditionedValues}. Each element of
   * {@code factors} defines a conditional marginal distribution over some set
   * of variables, given {@code conditionedValues}.
   * 
   * @param factors
   * @param partitionFunction
   */
  public FactorMarginalSet(Collection<Factor> factors, double partitionFunction,
      VariableNumMap conditionedVariables, Assignment conditionedValues) {
    super(getVariablesFromFactors(factors), conditionedVariables, conditionedValues);
    this.partitionFunction = partitionFunction;
    this.variableFactorMap = HashMultimap.create();
    for (Factor factor : factors) {
      for (Integer variableNum : factor.getVars().getVariableNums()) {
        variableFactorMap.put(variableNum, factor);
      }
    }
    this.allFactors = ImmutableList.copyOf(factors);
  }
  
  public static FactorMarginalSet fromAssignment(VariableNumMap conditionedVariables, Assignment conditionedValues) {
    return new FactorMarginalSet(Collections.<Factor>emptyList(), 1.0, conditionedVariables, conditionedValues);
  }
  
  private static VariableNumMap getVariablesFromFactors(Collection<Factor> factors) {
    VariableNumMap output = VariableNumMap.emptyMap();
    for (Factor factor : factors) {
      output = output.union(factor.getVars());
    }
    return output;
  }

  @Override
  public MarginalSet addConditionalVariables(Assignment values, VariableNumMap valueVariables) {
    Preconditions.checkArgument(valueVariables.containsAll(values.getVariableNums()));
    Assignment newValues = getConditionedValues().union(values);
    VariableNumMap newVariables = getConditionedVariables().union(
        valueVariables.intersection(values.getVariableNums()));
    return new FactorMarginalSet(allFactors, partitionFunction, newVariables, newValues);
  }

  @Override
  public Factor getMarginal(Collection<Integer> varNums) {
    // Special case if the input factor graph has no unassigned variables. 
    if (varNums.size() == 0 && allFactors.size() == 0) {
      return TableFactor.pointDistribution(VariableNumMap.emptyMap(), Assignment.EMPTY);
    }
    
    // Find a factor among the given factors that includes all of the given
    // variables.
    Set<Factor> relevantFactors = Sets.newHashSet(allFactors);
    for (Integer varNum : varNums) {
      relevantFactors.retainAll(variableFactorMap.get(varNum));
    }

    if (relevantFactors.size() == 0) {
      throw new RuntimeException("Graph does not contain a factor with all variables: " + varNums);
    }

    // Pick an arbitrary factor to use for the marginal
    Factor marginal = relevantFactors.iterator().next();

    // Marginalize out any remaining variables...
    Set<Integer> allVarNums = new HashSet<Integer>(marginal.getVars().getVariableNums());
    allVarNums.removeAll(varNums);
    return marginal.marginalize(allVarNums);
  }

  @Override
  public double getPartitionFunction() {
    return partitionFunction;
  }
}
