package com.jayantkrish.jklol.inference;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IntMultimap;

/**
 * Stores a set of marginal distributions as {@link Factor}s. Additionally, some
 * variables may take on single values, given by an {@code Assignment}.
 * 
 * @author jayant
 */
public class FactorMarginalSet extends AbstractMarginalSet {

  private final ImmutableList<Factor> allFactors;
  private final IntMultimap variableFactorMap;
  private final double logPartitionFunction;

  /**
   * Constructs a {@code FactorMarginalSet} conditioned on
   * {@code conditionedVars} taking {@code conditionedValues}. Each element of
   * {@code factors} defines a conditional marginal distribution over some set
   * of variables, given {@code conditionedValues}.
   * 
   * @param factors
   * @param logPartitionFunction
   */
  public FactorMarginalSet(Collection<Factor> factors, double logPartitionFunction,
      VariableNumMap conditionedVariables, Assignment conditionedValues) {
    super(getVariablesFromFactors(factors), conditionedVariables, conditionedValues);
    this.allFactors = ImmutableList.copyOf(factors);

    int numEntries = 0;
    for (int i = 0; i < allFactors.size(); i++) {
      numEntries += allFactors.get(i).getVars().size();
    }

    int[] keys = new int[numEntries];
    int[] values = new int[numEntries];
    int numFilled = 0;
    for (int i = 0; i < allFactors.size(); i++) {
      for (int variableNum : allFactors.get(i).getVars().getVariableNumsArray()) {
        keys[numFilled] = variableNum;
        values[numFilled] = i;
        numFilled++;
      }
    }
    this.variableFactorMap = IntMultimap.createFromUnsortedArrays(keys, values, 0);

    this.logPartitionFunction = logPartitionFunction;
  }

  /**
   * Constructs a marginal distribution over {@code conditionedVariables} that
   * assigns all of its weight to {@code conditionedValues}. {@code
   * partitionFunction} should be set to the unnormalized probability of {@code
   * conditionedValues}.
   *
   * @param conditionedVariables
   * @param conditionedValues
   * @param logPartitionFunction
   */
  public static FactorMarginalSet fromAssignment(VariableNumMap conditionedVariables, Assignment conditionedValues,
      double logPartitionFunction) {
    return new FactorMarginalSet(Collections.<Factor>emptyList(), logPartitionFunction, conditionedVariables, conditionedValues);
  }
  
  private static final VariableNumMap getVariablesFromFactors(Collection<Factor> factors) { 
    VariableNumMap output = VariableNumMap.EMPTY;
    for (Factor factor : factors) {
      output = output.union(factor.getVars());
    }
    return output;
  }

  @Override
  public Factor getMarginal(Collection<Integer> varNums) {
    Factor finalMarginal = getUnnormalizedMarginal(varNums);
    return finalMarginal.product(1.0 / finalMarginal.getTotalUnnormalizedProbability());
  }

  public Factor getUnnormalizedMarginal(Collection<Integer> varNums) {
    if (varNums.size() == 0 && allFactors.size() == 0) {
      // Special case if the inputVar factor graph has no unassigned variables. 
      return TableFactor.pointDistribution(VariableNumMap.EMPTY, Assignment.EMPTY);
    }

    // Find a factor among the given factors that includes all of the given
    // variables.
    int[] relevantFactors = new int[allFactors.size()];
    Arrays.fill(relevantFactors, 0);
    for (int varNum : varNums) {
      for (int factorNum : variableFactorMap.getArray(varNum)) {
        relevantFactors[factorNum]++;
      }
    }

    int factorNum = -1;
    int numVars = varNums.size();
    for (int i = 0; i < relevantFactors.length; i++) {
      if (relevantFactors[i] == numVars) {
        factorNum = i;
        break;
      }
    }

    if (factorNum == -1) {
      // This case requires a more advanced algorithm.
      return computeMarginalFromMultipleFactors(varNums);
    }

    // Pick an arbitrary factor to use for the marginal
    Factor marginal = allFactors.get(factorNum);

    // Marginalize out any remaining variables
    VariableNumMap toEliminate = marginal.getVars().removeAll(varNums);
    Factor finalMarginal = marginal.marginalize(toEliminate);
    return finalMarginal;
  }

  public Factor getUnnormalizedMarginal(int... varNums) {
    return getUnnormalizedMarginal(Ints.asList(varNums));
  }

  private Factor computeMarginalFromMultipleFactors(Collection<Integer> varNums) {
    throw new RuntimeException("Graph does not contain a factor with all variables: " + varNums);
  }

  @Override
  public double getLogPartitionFunction() {
    return logPartitionFunction;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (Factor factor : allFactors) {
      sb.append(factor.getParameterDescription());
    }
    sb.append("partition function=" + logPartitionFunction);
    return sb.toString();
  }
}
