package com.jayantkrish.jklol.training;

import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.evaluation.Example;
import com.jayantkrish.jklol.evaluation.FactorGraphPredictor;
import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.ObjectVariable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.VariablePattern;
import com.jayantkrish.jklol.models.loglinear.ConditionalLogLinearFactor;
import com.jayantkrish.jklol.models.loglinear.LogLinearModelBuilder;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Regression test for linear classifiers. This test trains a linear classifier
 * and tests its predictions.
 * 
 * @author jayantk
 */
public class LinearClassifierTest extends TestCase {

  ParametricFactorGraph linearClassifier;
  VariableNumMap x, y, all;

  List<Example<Assignment, Assignment>> trainingData;

  public void setUp() {
    LogLinearModelBuilder builder = new LogLinearModelBuilder();

    DiscreteVariable outputVar = new DiscreteVariable("tf",
        Arrays.asList("T", "F"));
    ObjectVariable tensorVar = new ObjectVariable(Tensor.class);

    builder.addVariable("x", tensorVar);
    builder.addDiscreteVariable("y", outputVar);

    x = builder.getVariables().getVariablesByName("x");
    y = builder.getVariables().getVariablesByName("y");
    all = x.union(y);

    builder.addFactor(new ConditionalLogLinearFactor(x, y, 4),
        VariablePattern.fromVariableNumMap(x.union(y)));

    linearClassifier = builder.build();

    trainingData = Lists.newArrayList();
    for (int i = 0; i < 8; i++) {
      double[] values = new double[4];
      values[0] = (i % 2) * 2 - 1;
      values[1] = ((i / 2) % 2) * 2 - 1;
      values[2] = ((i / 4) % 2) * 2 - 1;
      values[3] = 1;
      Assignment inputValue = x.outcomeArrayToAssignment(SparseTensor.vector(0, 4, values));

      // Output is "T" if the sum of the (non-bias) features is > 0.
      double sum = 0;
      for (int j = 0; j < 3; j++) {
        sum += values[j];
      }
      Assignment outputValue = y.outcomeArrayToAssignment((sum >= 0) ? "T" : "F");
      trainingData.add(Example.create(inputValue, outputValue));
    }
  }

  public void testTrainSvm() {
    SubgradientSvmTrainer trainer = new SubgradientSvmTrainer(80, 1, 1.0, new JunctionTree(),
        new SubgradientSvmTrainer.HammingCost(), null);

    SufficientStatistics parameters = trainer.train(linearClassifier, trainingData);
    FactorGraph trainedModel = linearClassifier.getFactorGraphFromParameters(parameters);

    // Should be able to get 0 training error.
    FactorGraphPredictor predictor = new FactorGraphPredictor(trainedModel, y, new JunctionTree());
    for (Example<Assignment, Assignment> trainingDatum : trainingData) {
      Assignment prediction = predictor.getBestPrediction(trainingDatum.getInput());
      assertEquals(trainingDatum.getOutput(), prediction);
    }
  }
  
  public void testTrainLogisticRegression() {
    StochasticGradientTrainer trainer = new StochasticGradientTrainer(new JunctionTree(), 80);

    SufficientStatistics parameters = trainer.train(linearClassifier, 
        linearClassifier.getNewSufficientStatistics(), trainingData);
    FactorGraph trainedModel = linearClassifier.getFactorGraphFromParameters(parameters);

    // Should be able to get 0 training error.
    FactorGraphPredictor predictor = new FactorGraphPredictor(trainedModel, y, new JunctionTree());
    for (Example<Assignment, Assignment> trainingDatum : trainingData) {
      Assignment prediction = predictor.getBestPrediction(trainingDatum.getInput());
      assertEquals(trainingDatum.getOutput(), prediction);
    }
  }
}
