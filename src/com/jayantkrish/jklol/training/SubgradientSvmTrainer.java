package com.jayantkrish.jklol.training;

import java.util.Iterator;
import java.util.List;

import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.evaluation.Example;
import com.jayantkrish.jklol.inference.FactorMarginalSet;
import com.jayantkrish.jklol.inference.MarginalCalculator;
import com.jayantkrish.jklol.inference.MarginalSet;
import com.jayantkrish.jklol.inference.MaxMarginalSet;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.util.AllAssignmentIterator;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Trains a {@link ParametricFactorGraph} as a structured SVM. The training
 * procedure performs (stochastic) subgradient descent on the structured SVM
 * objective function using a user-specified cost function and L2
 * regularization.
 * 
 * @author jayantk
 */
public class SubgradientSvmTrainer extends AbstractStochasticGradientTrainer
<ParametricFactorGraph, DynamicFactorGraph, Example<DynamicAssignment, DynamicAssignment>> {

  private final MarginalCalculator marginalCalculator;
  private final CostFunction costFunction;

  /**
   * If {@code regularizationConstant = 0}, {@code decayStepSize = false} and
   * the cost function is {@code ZeroCost}, then the structured SVM update rule
   * is equivalent to the structured perceptron.
   * 
   * @param marginalCalculator
   * @param costFunction
   * @param numIterations
   * @param batchSize
   * @param stepSize
   * @param decayStepSize
   * @param regularizationConstant
   * @param log
   */
  public SubgradientSvmTrainer(MarginalCalculator marginalCalculator, CostFunction costFunction,
      int numIterations, int batchSize, double stepSize, boolean decayStepSize,
      double regularizationConstant, LogFunction log) {
    super(numIterations, batchSize, stepSize, decayStepSize, regularizationConstant, log);

    this.marginalCalculator = marginalCalculator;
    this.costFunction = costFunction;
  }

  protected SufficientStatistics initializeGradient(ParametricFactorGraph modelFamily) {
    return modelFamily.getNewSufficientStatistics();
  }

  protected DynamicFactorGraph instantiateModel(ParametricFactorGraph modelFamily,
      SufficientStatistics parameters) {
    return modelFamily.getFactorGraphFromParameters(parameters);
  }

  /**
   * {@inheritDoc}
   * <p>
   * The structured SVM subgradient compares the unnormalized probability of the
   * output with the cost-augmented unnormalized probability of the best
   * prediction.
   */
  @Override
  protected void accumulateGradient(SufficientStatistics subgradient, DynamicFactorGraph currentDynamicModel,
      ParametricFactorGraph modelFamily, Example<DynamicAssignment, DynamicAssignment> example) {

    log.startTimer("dynamic_instantiation");
    FactorGraph currentModel = currentDynamicModel.getFactorGraph(example.getInput());
    Assignment input = currentDynamicModel.getVariables().toAssignment(example.getInput());
    Assignment observed = currentDynamicModel.getVariables().toAssignment(
        example.getOutput().union(example.getInput()));
    Assignment output = observed.removeAll(input.getVariableNums());
    VariableNumMap outputVariables = currentDynamicModel.getVariables().instantiateVariables(
        example.getInput()).intersection(output.getVariableNums());
    log.log(input, currentModel);
    log.log(observed, currentModel);
    log.stopTimer("dynamic_instantiation");

    // Get the cost-augmented best prediction based on the current input.
    Assignment outputAssignment = observed.removeAll(input.getVariableNums());

    log.startTimer("update_subgradient/cost_augment");
    FactorGraph costAugmentedModel = costFunction.augmentWithCosts(currentModel,
        currentModel.getVariables().intersection(outputAssignment.getVariableNums()),
        outputAssignment);
    log.stopTimer("update_subgradient/cost_augment");

    log.startTimer("update_subgradient/condition");
    FactorGraph conditionalCostAugmentedModel = costAugmentedModel.conditional(input);
    log.stopTimer("update_subgradient/condition");

    log.startTimer("update_subgradient/inference");
    MaxMarginalSet predicted = marginalCalculator.computeMaxMarginals(conditionalCostAugmentedModel);
    Assignment prediction = predicted.getNthBestAssignment(0);
    log.stopTimer("update_subgradient/inference");

    // Get the best value for any hidden variables, given the current input and
    // correct output.
    log.startTimer("update_subgradient/condition");
    // The costs are a function of the output variables, so it is okay to
    // condition the cost-augmented model with the output values. This approach
    // also avoids duplicating work for conditioning on the inputs.
    FactorGraph conditionalOutputModel = conditionalCostAugmentedModel.conditional(outputAssignment);
    log.stopTimer("update_subgradient/condition");

    log.startTimer("update_subgradient/inference");
    MaxMarginalSet actualMarginals = marginalCalculator.computeMaxMarginals(conditionalOutputModel);
    Assignment actual = actualMarginals.getNthBestAssignment(0);
    log.stopTimer("update_subgradient/inference");

    /*
     * log.log(iterationNum, iterationNum, input, currentModel);
     * log.log(iterationNum, iterationNum, prediction, currentModel);
     * log.log(iterationNum, iterationNum, actual, currentModel);
     */

    // TODO: delete these
    double predictedProb = conditionalCostAugmentedModel.getUnnormalizedLogProbability(prediction);
    double actualProb = conditionalCostAugmentedModel.getUnnormalizedLogProbability(actual);
    System.out.println("PROBS:" + predictedProb + " " + actualProb);

    Assignment predictedOutputValues = prediction.intersection(outputVariables);
    Assignment actualOutputValues = actual.intersection(outputVariables);

    System.out.println(outputVariables);
    System.out.println(predictedOutputValues);
    System.out.println(actualOutputValues);

    // Update parameters if necessary.
    if (!predictedOutputValues.equals(actualOutputValues)) {
      log.startTimer("update_subgradient/parameter_update");
      // Convert the assignments into marginal (point) distributions in order to
      // update the parameter vector.
      MarginalSet actualMarginal = FactorMarginalSet.fromAssignment(conditionalOutputModel.getAllVariables(), actual);
      MarginalSet predictedMarginal = FactorMarginalSet.fromAssignment(conditionalCostAugmentedModel.getAllVariables(), prediction);
      // Update the parameter vector
      modelFamily.incrementSufficientStatistics(subgradient, actualMarginal, 1.0);
      modelFamily.incrementSufficientStatistics(subgradient, predictedMarginal, -1.0);
      // System.out.println(modelFamily.getParameterDescription(subgradient));

      // Return the loss, which is the amount by which the prediction is within
      // the margin.
      /*
       * double loss = predictedProb - actualProb;
       * log.stopTimer("update_subgradient/parameter_update");
       * 
       * System.out.println("PROBS:" + predictedProb + " " + actualProb); return
       * loss;
       */
    }
    // Special value to signify that the example was correct.
    // return -1;
  }

  /**
   * The cost imposed on the SVM for selecting an incorrect value. This function
   * is used during training to add cost factors to the factor graph. The
   * resulting factor graph is then decoded to select the
   * "best and most dangerous" prediction of the SVM.
   * 
   * @author jayantk
   */
  public static interface CostFunction {

    /**
     * Returns {@code factorGraph} with additional cost factors added. Cost
     * factors add additional weight to incorrect labels, i.e., labels which are
     * not equal to {@code trueLabel}.
     * 
     * The added cost factors may include any variable in
     * {@code outputVariables}, but cannot include any other variable. That is,
     * the added cost is solely a function of the assignment to
     * {@code outputVariables} and {@code trueLabel}.
     * 
     * 
     * @param factorGraph
     * @param outputVariables
     * @param trueLabel
     * @return
     */
    public FactorGraph augmentWithCosts(FactorGraph factorGraph, VariableNumMap outputVariables, Assignment trueLabel);
  }

  /**
   * {@code HammingCost} is a per-variable cost function. The total added cost
   * for a predicted assignment is the number of variables in predicted whose
   * values do not agree with the true assignment. If {@code HammingCost} is
   * used for binary classification, it reduces to the standard hinge loss for
   * (non-structured) SVMs.
   * 
   * @author jayantk
   */
  public static class HammingCost implements CostFunction {
    public FactorGraph augmentWithCosts(FactorGraph factorGraph, VariableNumMap outputVariables, Assignment trueLabel) {
      FactorGraph augmentedGraph = factorGraph;
      for (Integer varNum : outputVariables.getVariableNums()) {
        List<Integer> varNumList = Ints.asList(varNum);
        TableFactorBuilder builder = new TableFactorBuilder(outputVariables.intersection(varNumList),
            SparseTensorBuilder.getFactory());

        Iterator<Assignment> varAssignments = new AllAssignmentIterator(outputVariables.intersection(varNumList));
        while (varAssignments.hasNext()) {
          builder.incrementWeight(varAssignments.next(), Math.E);
        }
        builder.multiplyWeight(trueLabel.intersection(varNumList), Math.exp(-1.0));

        augmentedGraph = augmentedGraph.addFactor("hamming_cost_factor-" + varNum, builder.build());
      }
      return augmentedGraph;
    }
  }

  /**
   * A cost function which ignores any errors. This cost function leaves the
   * input factor graph unchanged. Note that this reduces the structured SVM to
   * a structured perceptron.
   * 
   * @author jayantk
   */
  public static class ZeroCost implements CostFunction {
    public FactorGraph augmentWithCosts(FactorGraph factorGraph, VariableNumMap outputVariables, Assignment trueLabel) {
      return factorGraph;
    }
  }
}
