package com.jayantkrish.jklol.inference;

import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.IndexedList;

/**
 * Implementation of mean field variational inference. This is an approximate
 * inference algorithm suitable for computing (approximate) marginal
 * distributions for discrete-valued factor graphs.
 * <p>
 * Note that factors with 0 unnormalized probability assignments are not
 * supported by this algorithm.
 * 
 * @author jayantk
 */
public class MeanFieldVariational implements MarginalCalculator {
  private static final long serialVersionUID = 1L;

  private static final double CONVERGENCE_DELTA = 0.00000001; 
  // private static final double CONVERGENCE_DELTA = 0.01;

  @Override
  public MarginalSet computeMarginals(FactorGraph factorGraph) {
    VariableNumMap variables = factorGraph.getVariables();
    Preconditions.checkArgument(variables.getDiscreteVariables().size() == variables.size());

    // Initialize the mean field distribution to the uniform distribution over
    // all variables.
    long curTime = System.currentTimeMillis();
    System.out.println("init: " + (System.currentTimeMillis() - curTime)); 
    int numVars = variables.size();
    IndexedList<Integer> variableNums = new IndexedList<Integer>(variables.getVariableNums());
    List<DiscreteVariable> variableTypes = variables.getDiscreteVariables();
    List<Tensor> variableMarginals = Lists.newArrayListWithCapacity(numVars);
    for (int i = 0; i < numVars; i++) {
      int[] dimensions = new int[] { variableNums.get(i) };
      int[] sizes = new int[] { variableTypes.get(i).numValues() };
      variableMarginals.add(DenseTensor.constant(dimensions, sizes, 1.0 / sizes[0]));
    }
    System.out.println("logweights: "  + (System.currentTimeMillis() - curTime));
    // Get the log weights for each factor in the original factor graph.
    List<Tensor> logWeights = Lists.newArrayList();
    for (Factor factor : factorGraph.getFactors()) {
      logWeights.add(factor.coerceToDiscrete().getWeights().elementwiseLog());
    }
    System.out.println("done: " +  + (System.currentTimeMillis() - curTime)); 

    double updateL2 = Double.POSITIVE_INFINITY;
    int numIterations = 0;
    while (updateL2 > CONVERGENCE_DELTA) {
      updateL2 = 0.0;
      for (int i = 0; i < numVars; i++) {
        int curVarNum = variableNums.get(i);
        // Accumulate the messages from each factor containing this variable.
        DenseTensorBuilder messageAccumulator = new DenseTensorBuilder(
            variableMarginals.get(i).getDimensionNumbers(), variableMarginals.get(i).getDimensionSizes());
        Set<Integer> factorIndexes = factorGraph.getFactorsWithVariable(curVarNum);
        // System.out.println(i + " " + curVarNum + ": " + factorIndexes);
        for (int factorIndex : factorIndexes) {
          Tensor factorMessage = getFactorMessage(curVarNum, logWeights.get(factorIndex),
              variableMarginals, variableNums);
          // System.out.println("factorMessage: " +
          // Arrays.toString(factorMessage.getDimensionNumbers()));
          // System.out.println("messageAccumulator: " +
          // Arrays.toString(messageAccumulator.getDimensionNumbers()));
          messageAccumulator.increment(factorMessage);
        }

        // Update the marginal based on the inbound messages, setting
        // marginal equal to the logistic function of the accumulated messages.
        messageAccumulator.exp();
        double normalizingConstant = messageAccumulator.getTrace();
        messageAccumulator.multiply(1.0 / normalizingConstant);
        Tensor newMarginal = messageAccumulator.buildNoCopy();

        // Compute the convergence criteria
        Tensor delta = newMarginal.elementwiseAddition(
            variableMarginals.get(i).elementwiseProduct(SparseTensor.getScalarConstant(-1.0)));
        updateL2 += delta.getL2Norm();

        variableMarginals.set(i, newMarginal);
      }
      numIterations++;
      System.out.println("update L2: " + updateL2);
    }
    
    System.out.println("iterations:" + numIterations);
    // Format output as factors.
    List<Factor> marginals = Lists.newArrayList();
    for (int i = 0; i < numVars; i++) {
      marginals.add(new TableFactor(variables.intersection(variableNums.get(i)),
          variableMarginals.get(i)));
    }

    System.out.println("really done: " +  + (System.currentTimeMillis() - curTime));
    return new FactorMarginalSet(marginals, 1.0, factorGraph.getConditionedVariables(),
        factorGraph.getConditionedValues());
  }

  /**
   * Computes the message from {@code logFactorWeights} to {@code curVarNum}
   * using the current values of {@code variableMarginals}.
   * 
   * @param curVarNum
   * @param logFactorWeights
   * @param variableMarginals
   * @param variableNums
   * @return
   */
  private static final Tensor getFactorMessage(int curVarNum, Tensor logFactorWeights,
      List<Tensor> variableMarginals, IndexedList<Integer> variableNums) {
    Tensor factorMessage = logFactorWeights;
    // Each message is the outer product of all variable marginals, except
    // variable i, elementwise multiplied by the log factor weights.
    int[] weightVariableNums = factorMessage.getDimensionNumbers();
    List<Integer> variablesToMarginalize = Lists.newArrayList();
    for (int j = 0; j < weightVariableNums.length; j++) {
      if (weightVariableNums[j] == curVarNum) {
        continue;
      }

      Tensor variableMarginal = variableMarginals.get(variableNums.getIndex(weightVariableNums[j]));
      factorMessage = factorMessage.elementwiseProduct(variableMarginal);
      variablesToMarginalize.add(weightVariableNums[j]);
    }
    return factorMessage.sumOutDimensions(variablesToMarginalize);
  }

  @Override
  public MaxMarginalSet computeMaxMarginals(FactorGraph factorGraph) {
    throw new UnsupportedOperationException("Not supported by variational inference");
  }
}
