package com.jayantkrish.jklol.training;

import java.util.Iterator;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.evaluation.Example;
import com.jayantkrish.jklol.inference.FactorMarginalSet;
import com.jayantkrish.jklol.inference.MarginalCalculator;
import com.jayantkrish.jklol.inference.MarginalSet;
import com.jayantkrish.jklol.inference.MaxMarginalSet;
import com.jayantkrish.jklol.inference.MaxMarginalSet.ZeroProbabilityError;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
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
public class SubgradientSvmTrainer extends AbstractTrainer {

  private final int numIterations;
  private final int batchSize;
  private final double regularization;
  private final MarginalCalculator marginalCalculator;
  private final CostFunction costFunction;
  private final LogFunction log;

  public SubgradientSvmTrainer(int numIterations, int batchSize,
      double regularizationConstant, MarginalCalculator marginalCalculator,
      CostFunction costFunction, LogFunction log) {
    Preconditions.checkArgument(regularizationConstant > 0);

    this.numIterations = numIterations;
    this.batchSize = batchSize;
    this.regularization = regularizationConstant;
    this.marginalCalculator = marginalCalculator;
    this.costFunction = costFunction;
    this.log = (log != null) ? log : new NullLogFunction();
  }

  /**
   * {@inheritDoc}
   * 
   * {@code modelFamily} is presumed to be a loglinear model.
   * {@code trainingData} contains the inputVar/outputVar pairs for training.
   * The inputVar and outputVar of each training example should be over disjoint
   * sets of variables, and the union of these sets should contain all of the
   * variables in {@code modelFamily}.
   * 
   * @param modelFamily
   * @param trainingData
   * @return
   */
  public SufficientStatistics train(ParametricFactorGraph modelFamily,
      SufficientStatistics initialParameters,
      Iterable<Example<DynamicAssignment, DynamicAssignment>> trainingData) {
    SufficientStatistics parameters = initialParameters;

    // cycledTrainingData loops indefinitely over the elements of trainingData.
    // This is desirable because we want batchSize examples but don't
    // particularly care where in trainingData they come from.
    Iterator<Example<DynamicAssignment, DynamicAssignment>> cycledTrainingData =
        Iterators.cycle(trainingData);

    // Each iteration processes a single batch of batchSize training examples.
    for (int i = 0; i < numIterations; i++) {
      log.notifyIterationStart(i);
      // Get the examples for this batch. Ideally, this would be a random
      // sample; however, deterministically iterating over the examples may be
      // more efficient and is fairly close if the examples are provided in
      // random order.
      log.startTimer("factor_graph_from_parameters");
      List<Example<DynamicAssignment, DynamicAssignment>> batchData = getBatch(cycledTrainingData, batchSize);
      DynamicFactorGraph currentDynamicModel = modelFamily.getFactorGraphFromParameters(parameters);
      SufficientStatistics subgradient = modelFamily.getNewSufficientStatistics();
      log.stopTimer("factor_graph_from_parameters");
      int numIncorrect = 0;
      double approximateObjectiveValue = regularization * Math.pow(parameters.getL2Norm(), 2) / 2;
      int searchErrors = 0;
      for (Example<DynamicAssignment, DynamicAssignment> example : batchData) {
        log.startTimer("dynamic_instantiation");
        FactorGraph currentModel = currentDynamicModel.getFactorGraph(example.getInput());
        Assignment input = currentDynamicModel.getVariables().toAssignment(example.getInput());
        Assignment observed = currentDynamicModel.getVariables().toAssignment(example.getOutput().union(example.getInput()));
        log.log(input, currentModel);
        log.log(observed, currentModel);
        log.stopTimer("dynamic_instantiation");

        log.startTimer("update_subgradient");
        try {
          double objectiveValue = updateSubgradientWithInstance(i, currentModel, input, observed, modelFamily, subgradient);
          if (objectiveValue != 0.0) {
            numIncorrect++;
            approximateObjectiveValue += objectiveValue / batchSize;
          }
        } catch (ZeroProbabilityError e) {
          // Search error -- could not find positive probability assignments to the graphical model.
          searchErrors++;
        }
        log.stopTimer("update_subgradient");
      }

      // TODO: Can we use the Pegasos projection step?
      // If so, the step size should decay as 1/i, not 1/sqrt(i).
      log.startTimer("parameter_update");
      double stepSize = 1.0 / (regularization * Math.sqrt(i + 2));  
      parameters.multiply(1.0 - (stepSize * regularization));
      parameters.increment(subgradient, stepSize / batchSize);
      log.stopTimer("parameter_update");

      log.logStatistic(i, "number of examples within margin", Integer.toString(numIncorrect));
      log.logStatistic(i, "approximate objective value", Double.toString(approximateObjectiveValue));
      log.logStatistic(i, "search errors", Integer.toString(searchErrors));
      log.notifyIterationEnd(i);
    }
    return parameters;
  }

  /**
   * Computes the subgradient of the given training example ({@code input} and
   * {@code output}) and adds it to {@code subgradient}. Returns the structured
   * hinge loss of the prediction vs. the actual output.
   * 
   * @param currentModel
   * @param input
   * @param output
   * @param modelFamily
   * @param subgradient
   * @return
   */
  private double updateSubgradientWithInstance(int iterationNum, FactorGraph currentModel,
      Assignment input, Assignment observed, ParametricFactorGraph modelFamily,
      SufficientStatistics subgradient) {
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

    log.log(iterationNum, iterationNum, input, currentModel);
    log.log(iterationNum, iterationNum, prediction, currentModel);
    log.log(iterationNum, iterationNum, actual, currentModel);

    // Update parameters if necessary.
    if (!prediction.equals(actual)) {
      log.startTimer("update_subgradient/parameter_update");
      // Convert the assignments into marginal (point) distributions in order to
      // update the parameter vector.
      MarginalSet actualMarginal = FactorMarginalSet.fromAssignment(conditionalOutputModel.getAllVariables(), actual);
      MarginalSet predictedMarginal = FactorMarginalSet.fromAssignment(conditionalCostAugmentedModel.getAllVariables(), prediction);
      // Update the parameter vector
      modelFamily.incrementSufficientStatistics(subgradient, actualMarginal, 1.0);
      modelFamily.incrementSufficientStatistics(subgradient, predictedMarginal, -1.0);
      // Return the loss, which is the amount by which the prediction is within
      // the margin.
      double predictedProb = conditionalCostAugmentedModel.getUnnormalizedLogProbability(prediction);
      double actualProb = conditionalCostAugmentedModel.getUnnormalizedLogProbability(actual);
      double loss = predictedProb - actualProb;
      log.stopTimer("update_subgradient/parameter_update");

      System.out.println("PROBS:" + predictedProb + " " + actualProb);
      return loss;
    }
    return 0.0;
  }

  private <P, Q> List<Example<P, Q>> getBatch(
      Iterator<Example<P, Q>> trainingData, int batchSize) {
    List<Example<P, Q>> batchData = Lists.newArrayListWithCapacity(batchSize);
    for (int i = 0; i < batchSize && trainingData.hasNext(); i++) {
      batchData.add(trainingData.next());
    }
    return batchData;
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
        TableFactorBuilder builder = new TableFactorBuilder(outputVariables.intersection(varNumList));

        Iterator<Assignment> varAssignments = new AllAssignmentIterator(outputVariables.intersection(varNumList));
        while (varAssignments.hasNext()) {
          builder.incrementWeight(varAssignments.next(), Math.E);
        }
        builder.multiplyWeight(trueLabel.intersection(varNumList), Math.exp(-1.0));

        augmentedGraph = augmentedGraph.addFactor(builder.build());
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
