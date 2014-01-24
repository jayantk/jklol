package com.jayantkrish.jklol.inference;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.TensorBuilder;
import com.jayantkrish.jklol.util.IndexedList;

/**
 * Approximate inference technique for computing the MAP (highest weight)
 * assignment to a graphical model. This marginal calculator only supports
 * computing max-marginals.
 * 
 * @author jayantk
 */
public class DualDecomposition implements MarginalCalculator {
  private static final long serialVersionUID = 1L;

  private final int maxIterations;

  public DualDecomposition(int maxIterations) {
    Preconditions.checkArgument(maxIterations >= 1);
    this.maxIterations = maxIterations;
  }

  @Override
  public MarginalSet computeMarginals(FactorGraph factorGraph) {
    throw new UnsupportedOperationException("Cannot use DualDecomposition to compute marginals.");
  }

  @Override
  public MaxMarginalSet computeMaxMarginals(FactorGraph factorGraph) {
    VariableNumMap variables = factorGraph.getVariables();
    Preconditions.checkArgument(variables.getDiscreteVariables().size() == variables.size());

    // Initialize the per-variable weights. These weights represent the sum of
    // any unary factors over the variable and all Lagrange multiplier factors.
    int numVars = variables.size();
    IndexedList<Integer> variableNums = new IndexedList<Integer>(variables.getVariableNums());
    List<DiscreteVariable> variableTypes = variables.getDiscreteVariables();
    List<TensorBuilder> variableWeights = Lists.newArrayListWithCapacity(numVars);
    for (int i = 0; i < numVars; i++) {
      int[] dimensions = new int[] { variableNums.get(i) };
      int[] sizes = new int[] { variableTypes.get(i).numValues() };
      DenseTensorBuilder weights = new DenseTensorBuilder(dimensions, sizes);

      // Integrate all unary factors into the variable weights.
      for (int factorIndex : factorGraph.getFactorsWithVariable(variableNums.get(i))) {
        Factor factor = factorGraph.getFactor(factorIndex);
        if (factor.getVars().size() == 1) {
          weights.increment(factor.coerceToDiscrete().getWeights().elementwiseLog());
        }
      }
      variableWeights.add(weights);
    }

    // Initialize per-factor weights, which again are the sum of the factors
    // plus Lagrange multipliers.
    List<TensorBuilder> factorWeights = Lists.newArrayList();
    for (Factor factor : factorGraph.getFactors()) {
      if (factor.getVars().size() == 1) {
        // Unary factors have already been incorporated into the variable
        // weights.
        continue;
      }
      factorWeights.add(DenseTensorBuilder.copyOf(
          factor.coerceToDiscrete().getWeights().elementwiseLog()));
    }

    int numDisagreements = 1;
    for (int i = 0; i < maxIterations && numDisagreements > 0; i++) {
      /*
      for (int j = 0; j < numVars; j++) {
        System.out.println(variableWeights.get(j));
      }
      for (int j = 0; j < factorWeights.size(); j++) {
        System.out.println(factorWeights.get(j));
      }
      */

      numDisagreements = gradientUpdate(factorWeights, variableWeights, 0.1 / Math.sqrt(i + 2));
      // System.out.println("ITERATION " + i + ": " + numDisagreements + " disagreements");
    }

    // Locally decode factors to an assignment.
    int[] variableDims = new int[variableWeights.size()];
    int[] variableSizes = new int[variableWeights.size()];
    int[] variableValues = new int[variableWeights.size()];
    locallyDecodeFactors(variableWeights, variableDims, variableSizes, variableValues);

    return new AssignmentMaxMarginalSet(variables.intArrayToAssignment(variableValues)
        .union(factorGraph.getConditionedValues()));
  }

  private void locallyDecodeFactors(List<TensorBuilder> unaryFactors,
      int[] variableNums, int[] variableSizes, int[] variableValues) {
    for (int i = 0; i < unaryFactors.size(); i++) {
      TensorBuilder unaryFactor = unaryFactors.get(i);
      variableNums[i] = unaryFactor.getDimensionNumbers()[0];
      variableSizes[i] = unaryFactor.getDimensionSizes()[0];
      variableValues[i] = unaryFactor.keyNumToDimKey(unaryFactor.getLargestValues(1)[0])[0];
    }
  }

  /**
   * Perform a single subgradient step, updating {@code factors} and
   * {@code unaryFactors} with the computed subgradient.
   * 
   * @param factors
   * @param unaryFactors
   * @param stepSize
   * @return
   */
  private int gradientUpdate(List<TensorBuilder> factors,
      List<TensorBuilder> unaryFactors, double stepSize) {
    // The best assignment, as computed from only the unary factors.
    int[] variableNums = new int[unaryFactors.size()];
    int[] variableSizes = new int[unaryFactors.size()];
    int[] variableValues = new int[unaryFactors.size()];
    locallyDecodeFactors(unaryFactors, variableNums, variableSizes, variableValues);

    // Identify where unary factors disagree with larger factors to compute the
    // subgradient.
    List<Integer> variableDisagreementList = Lists.newArrayList();
    List<Integer> factorDisagreementList = Lists.newArrayList();
    List<Integer> factorValueList = Lists.newArrayList();
    for (int i = 0; i < factors.size(); i++) {
      TensorBuilder factor = factors.get(i);
      int[] bestFactorValues = factor.keyNumToDimKey(factor.getLargestValues(1)[0]);
      int[] factorVariableNums = factor.getDimensionNumbers();

      for (int j = 0; j < factorVariableNums.length; j++) {
        int index = Arrays.binarySearch(variableNums, factorVariableNums[j]);
        if (bestFactorValues[j] != variableValues[index]) {
          // Factor and unary assignments disagree on variableNums[index].
          // Store the disagreement, used later to compute the subgradient.
          variableDisagreementList.add(index);
          factorDisagreementList.add(i);
          factorValueList.add(bestFactorValues[j]);

          /*
          System.out.println("Disagreement " + Arrays.toString(factorVariableNums) + "=" + Arrays.toString(bestFactorValues) + ", "
              + variableNums[index] + "=" + variableValues[index]);
              */
        }
      }
    }

    // Update the factor lagrange multipliers based on the disagreements.
    // For each disagreement, the subgradient update decreases the weight of
    // each factor's maximum weight assignment, and increases the weight of
    // the disagreeing factor's maximum weight assignment.
    for (int i = 0; i < variableDisagreementList.size(); i++) {
      int variableIndex = variableDisagreementList.get(i);
      int factorNum = factorDisagreementList.get(i);

      TensorBuilder factor = factors.get(factorNum);
      TensorBuilder unaryFactor = unaryFactors.get(variableIndex);

      int variableNum = variableNums[variableIndex];
      int variableSize = variableSizes[variableIndex];
      int bestFactorValue = factorValueList.get(i);
      int bestUnaryValue = variableValues[variableIndex];

      SparseTensor unaryGradient = SparseTensor.singleElement(new int[] { variableNum },
          new int[] { variableSize }, new int[] { bestUnaryValue }, 1);
      SparseTensor factorGradient = SparseTensor.singleElement(new int[] { variableNum },
          new int[] { variableSize }, new int[] { bestFactorValue }, 1);

      unaryFactor.incrementWithMultiplier(unaryGradient, -1.0 * stepSize);
      unaryFactor.incrementWithMultiplier(factorGradient, stepSize);
      factor.incrementWithMultiplier(unaryGradient, stepSize);
      factor.incrementWithMultiplier(factorGradient, -1.0 * stepSize);
    }

    return variableDisagreementList.size();
  }
}
