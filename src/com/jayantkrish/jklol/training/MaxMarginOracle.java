package com.jayantkrish.jklol.training;

import java.util.Iterator;
import java.util.List;

import com.google.common.base.Preconditions;
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

public class MaxMarginOracle implements GradientOracle<DynamicFactorGraph,
    Example<DynamicAssignment, DynamicAssignment>> {

  private final ParametricFactorGraph family;
  private final CostFunction costFunction;
  private final MarginalCalculator marginalCalculator;

  public MaxMarginOracle(ParametricFactorGraph family, CostFunction costFunction,
      MarginalCalculator marginalCalculator) {
    this.family = Preconditions.checkNotNull(family);
    this.costFunction = Preconditions.checkNotNull(costFunction);
    this.marginalCalculator = Preconditions.checkNotNull(marginalCalculator);
  }

  @Override
  public SufficientStatistics initializeGradient() {
    return family.getNewSufficientStatistics();
  }

  @Override
  public DynamicFactorGraph instantiateModel(SufficientStatistics parameters) {
    return family.getFactorGraphFromParameters(parameters);
  }

  @Override
  public double accumulateGradient(SufficientStatistics subgradient, DynamicFactorGraph currentDynamicModel,
      Example<DynamicAssignment, DynamicAssignment> example, LogFunction log) {

    log.startTimer("dynamic_instantiation");
    FactorGraph currentModel = currentDynamicModel.getFactorGraph(example.getInput());
    Assignment input = currentDynamicModel.getVariables().toAssignment(example.getInput());
    Assignment observed = currentDynamicModel.getVariables().toAssignment(
        example.getOutput().union(example.getInput()));
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

    double predictedProb = conditionalCostAugmentedModel.getUnnormalizedLogProbability(prediction);
    double actualProb = conditionalCostAugmentedModel.getUnnormalizedLogProbability(actual);

    // Update parameters if necessary.
    if (!actual.equals(prediction)) {
      log.startTimer("update_subgradient/increment_subgradient");
      // Convert the assignments into marginal (point) distributions in order to
      // update the parameter vector.
      MarginalSet actualMarginal = FactorMarginalSet.fromAssignment(conditionalOutputModel.getAllVariables(), actual, 1.0);
      MarginalSet predictedMarginal = FactorMarginalSet.fromAssignment(conditionalCostAugmentedModel.getAllVariables(), prediction, 1.0);
      // Update the parameter vector
      family.incrementSufficientStatistics(subgradient, actualMarginal, 1.0);
      family.incrementSufficientStatistics(subgradient, predictedMarginal, -1.0);
      
      log.stopTimer("update_subgradient/increment_subgradient");
    }

    // Return the negative hinge loss, which is the amount by which the
    // prediction is within the margin. (Negated, because GradientOracles
    // represent maximization problems).
    return Math.min(0.0, actualProb - predictedProb);
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
        // Set the cost of the true label to 0. (It was multiplied by e in the loop.)
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