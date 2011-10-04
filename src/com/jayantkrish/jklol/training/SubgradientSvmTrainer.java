package com.jayantkrish.jklol.training;

import java.util.Iterator;
import java.util.List;

import com.google.common.base.Supplier;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.evaluation.Example;
import com.jayantkrish.jklol.inference.MarginalCalculator;
import com.jayantkrish.jklol.inference.MaxMarginalSet;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.util.Assignment;

public class SubgradientSvmTrainer {

  private final int numIterations;
  private final int batchSize;
  private final double regularization;
  private final Supplier<MarginalCalculator> marginalCalculatorSupplier;
  
  public SubgradientSvmTrainer(int numIterations, int batchSize,
      double regularizationConstant, Supplier<MarginalCalculator> marginalCalculatorSupplier) {
    this.numIterations = numIterations;
    this.batchSize = batchSize;
    this.regularization = regularizationConstant;
    this.marginalCalculatorSupplier = marginalCalculatorSupplier;
  }
  
  public SufficientStatistics train(ParametricFactorGraph modelFamily, 
      Iterable<Example<Assignment, Assignment>> trainingData) {
    MarginalCalculator marginalCalculator = marginalCalculatorSupplier.get();
    SufficientStatistics parameters = modelFamily.getNewSufficientStatistics();
    
    // cycledTrainingData loops indefinitely over the elements of trainingData.
    // This is desirable because we want batchSize examples but don't particularly
    // care where in trainingData they come from.
    Iterator<Example<Assignment, Assignment>> cycledTrainingData = Iterators.cycle(trainingData); 

    // Each iteration processes a single batch of batchSize training examples.
    for (int i = 0; i < numIterations; i++) {
      // Get the examples for this batch. Ideally, this would be a random sample; however, 
      // deterministically iterating over the examples may be more efficient and is fairly close
      // if the examples are provided in random order.
      List<Example<Assignment, Assignment>> batchData = getBatch(cycledTrainingData, batchSize);
      
      FactorGraph currentModel = modelFamily.getFactorGraphFromParameters(parameters);
      SufficientStatistics subgradient = modelFamily.getNewSufficientStatistics();
      for (Example<Assignment, Assignment> example : batchData) {
        // Get the cost-augmented best prediction for the current example
        // TODO: augment model with costs conditioned on the label.
        MaxMarginalSet predicted = marginalCalculator.computeMaxMarginals(currentModel, example.getInput());
        Assignment prediction = predicted.getNthBestAssignment(0);
        Assignment actual = example.getOutput().jointAssignment(example.getInput());
          
        if (!prediction.equals(actual)) {
          subgradient.increment(modelFamily.computeSufficientStatistics(actual, 1.0), 1.0);
          subgradient.increment(modelFamily.computeSufficientStatistics(prediction, 1.0), -1.0);
        }
      }
      
      parameters.elementwiseMultiply(1.0 - (1.0 / i));
      parameters.increment(subgradient, 1.0 / (regularization * i * batchSize));
    }
  }
  
  private List<Example<Assignment, Assignment>> getBatch(
      Iterator<Example<Assignment, Assignment>> trainingData, int batchSize) {
    List<Example<Assignment, Assignment>> batchData = Lists.newArrayListWithCapacity(batchSize);
    for (int i = 0; i < batchSize && trainingData.hasNext(); i++) {
      batchData.add(trainingData.next());
    }
    return batchData;
  }
  
  private FactorGraph augmentWithCosts(FactorGraph factorGraph, List<Factor> costFactors) {
    FactorGraph augmentedGraph = factorGraph;
    for (Factor costFactor : costFactors) {
      augmentedGraph.addFactor(costFactor);
    }
    return augmentedGraph;
  }
}
