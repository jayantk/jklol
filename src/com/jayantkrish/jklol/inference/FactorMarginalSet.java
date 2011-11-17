package com.jayantkrish.jklol.inference;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.models.Factor;
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
      Assignment conditionedValues) {
    super(conditionedValues);
    this.partitionFunction = partitionFunction;
    this.variableFactorMap = HashMultimap.create();
    for (Factor factor : factors) {
      for (Integer variableNum : factor.getVars().getVariableNums()) {
        variableFactorMap.put(variableNum, factor);
      }
    }
    this.allFactors = ImmutableList.copyOf(factors);
  }

  @Override
  public MarginalSet addConditionalVariables(Assignment values) {
    Assignment newValues = getConditionedValues().jointAssignment(values);
    return new FactorMarginalSet(allFactors, partitionFunction, newValues);
  }

  @Override
  public Factor getMarginal(Collection<Integer> varNums) {
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
