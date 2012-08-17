package com.jayantkrish.jklol.training;

import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.evaluation.Example;
import com.jayantkrish.jklol.evaluation.FactorGraphPredictor.SimpleFactorGraphPredictor;
import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.ObjectVariable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.dynamic.WrapperVariablePattern;
import com.jayantkrish.jklol.models.loglinear.ConditionalLogLinearFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraphBuilder;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
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
    ParametricFactorGraphBuilder builder = new ParametricFactorGraphBuilder();

    DiscreteVariable outputVar = new DiscreteVariable("tf",
        Arrays.asList("T", "F"));
    ObjectVariable tensorVar = new ObjectVariable(Tensor.class);

    builder.addVariable("x", tensorVar);
    builder.addVariable("y", outputVar);

    x = builder.getVariables().getVariablesByName("x");
    y = builder.getVariables().getVariablesByName("y");
    all = x.union(y);

    DiscreteVariable featureVar = new DiscreteVariable("features", 
        Arrays.asList("f1", "f2", "f3", "f4"));
    
    builder.addFactor("classifier", new ConditionalLogLinearFactor(x, y, y, 
        featureVar, SparseTensorBuilder.getFactory()), new WrapperVariablePattern(x.union(y)));

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
    runTrainerTest(new MaxMarginOracle(linearClassifier, new MaxMarginOracle.HammingCost(), new JunctionTree()));
  }

  public void testTrainLogisticRegression() {
    runTrainerTest(new LoglikelihoodOracle(linearClassifier, new JunctionTree()));
  }

  private void runTrainerTest(GradientOracle<DynamicFactorGraph, Example<DynamicAssignment, DynamicAssignment>> oracle) {
    GradientOracle<DynamicFactorGraph, Example<Assignment, Assignment>> adaptedOracle = 
        OracleAdapter.createAssignmentAdapter(oracle);
    
    StochasticGradientTrainer trainer = new StochasticGradientTrainer(80, 1, 1.0, true, 0.1, new DefaultLogFunction());
    
    SufficientStatistics parameters = trainer.train(adaptedOracle,
        linearClassifier.getNewSufficientStatistics(), trainingData);
    FactorGraph trainedModel = linearClassifier.getFactorGraphFromParameters(parameters)
        .getFactorGraph(DynamicAssignment.EMPTY);
    
    System.out.println(parameters);

    // Should be able to get 0 training error.
    SimpleFactorGraphPredictor predictor = new SimpleFactorGraphPredictor(
        trainedModel, y, new JunctionTree());
    for (Example<Assignment, Assignment> trainingDatum : trainingData) {
      Assignment prediction = predictor.getBestPrediction(trainingDatum.getInput()).getBestPrediction();
      assertEquals(trainingDatum.getOutput(), prediction);
    }
  }
}
