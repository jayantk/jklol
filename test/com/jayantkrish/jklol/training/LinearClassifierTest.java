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
import com.jayantkrish.jklol.models.loglinear.ConditionalLogLinearFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraphBuilder;
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
  
  private static final int NUM_DIMS=10000;

  public void setUp() {
    // A linear classifier is represented as a parametric factor graph with
    // two variables: an input variable (x) whose values are feature vectors
    // and an output variable (y) whose values are the possible labels.
    // Build this factor graph using the following builder:
    ParametricFactorGraphBuilder builder = new ParametricFactorGraphBuilder();
    // Define the output variable type (i.e., the set of possible labels).
    DiscreteVariable outputVar = new DiscreteVariable("tf", Arrays.asList("T", "F"));
    // Define the input variable type (i.e., any Tensor).
    ObjectVariable tensorVar = new ObjectVariable(Tensor.class);

    // Add the variables to the factor graph being built, and
    // get references to the variables just created.
    builder.addVariable("x", tensorVar);
    builder.addVariable("y", outputVar);
    x = builder.getVariables().getVariablesByName("x");
    y = builder.getVariables().getVariablesByName("y");
    // This is both variables x and y.
    all = x.union(y);

    // Define the names of the features used in the classifier. Our classifier
    // will expect input vectors of dimension NUM_DIMS. This DiscreteVariable maps each
    // feature name to an index in the feature vector; it can also be used to
    // construct the input vectors.
    DiscreteVariable featureVar = DiscreteVariable.sequence("features", NUM_DIMS);

    // A ConditionalLogLinearFactor represents a trainable linear classifier
    // (yes, the name is terrible). Just copy this definition, replacing x, y
    // and featureVar with whatever you called those things.
    builder.addUnreplicatedFactor("classifier", new ConditionalLogLinearFactor(x, y, y,
        featureVar));
    // Builds the actual trainable model.
    linearClassifier = builder.build();

    // Construct some training data. This just builds vectors directly, but if
    // you have a file of feature vectors represented in terms of
    // feature names, consider using TableFactor.fromDelimitedFile
    trainingData = Lists.newArrayList();
    for (int i = 0; i < 8; i++) {
      double[] values = new double[NUM_DIMS];
      values[0] = (i % 2) * 2 - 1;
      values[1] = ((i / 2) % 2) * 2 - 1;
      values[2] = ((i / 4) % 2) * 2 - 1;
      values[3] = 1;
      
      Assignment inputValue = x.outcomeArrayToAssignment(SparseTensor.vector(0, NUM_DIMS, values));
      
      // Output is "T" if the sum of the (non-bias) features is > 0.
      double sum = 0;
      for (int j = 0; j < 3; j++) {
        sum += values[j];
      }

      // Training data points are represented as pairs of Assignments, which are
      // mappings from variables to their values. The input assignment
      // (inputValue) is the value of x, and outputValue is the thing to
      // predict. The input/output pair is encapsulated in an Example.
      Assignment outputValue = y.outcomeArrayToAssignment((sum >= 0) ? "T" : "F");
      trainingData.add(Example.create(inputValue, outputValue));
    }
  }

  public void testTrainSvm() {
    // Linear classifiers (or factor graphs generally) can be trained using
    // different objective functions. This is a max-margin objective, i.e., an
    // SVM.
    runTrainerTest(new MaxMarginOracle(linearClassifier, new MaxMarginOracle.HammingCost(), new JunctionTree()), false);
  }

  public void testTrainLogisticRegression() {
    // This creates a loglikelihood objective function to optimize.
    // The "new JunctionTree()" tells the objective how to compute
    // marginals in the model during training.
    runTrainerTest(new LoglikelihoodOracle(linearClassifier, new JunctionTree()), false);
  }
  
  public void testTrainLogisticRegressionLbfgs() {
    runTrainerTest(new LoglikelihoodOracle(linearClassifier, new JunctionTree()), true);
  }

  private void runTrainerTest(GradientOracle<DynamicFactorGraph, Example<DynamicAssignment, DynamicAssignment>> oracle,
      boolean useLbfgs) {
    // Our factor graph isn't dynamic (i.e., has no plates), so wrap the oracle
    // to accept Assignments (instead of DynamicAssignments, which are required
    // for plate models).
    GradientOracle<DynamicFactorGraph, Example<Assignment, Assignment>> adaptedOracle =
        OracleAdapter.createAssignmentAdapter(oracle);

    SufficientStatistics parameters = null;
    if (useLbfgs) {
      // Instantiate the optimization algorithm, in this case stochastic gradient
      // with l2 regularization.
      Lbfgs trainer = new Lbfgs(50, 10, 0.0, new DefaultLogFunction(1, false));
      
      // Estimate classifier parameters.
      parameters = trainer.train(adaptedOracle,
          linearClassifier.getNewSufficientStatistics(), trainingData);
    } else {
      // Instantiate the optimization algorithm, in this case stochastic gradient
      // with l2 regularization.
      StochasticGradientTrainer trainer = StochasticGradientTrainer.createWithL2Regularization(1000, 
          1, 1.0, true, 0.1, new DefaultLogFunction(10000, false));
      // Estimate classifier parameters.
      parameters = trainer.train(adaptedOracle,
          linearClassifier.getNewSufficientStatistics(), trainingData);
    }
    // Get the trained classifier as a factor graph (enabling inference). The
    // "getFactorGraph()" part again exists to support dynamic factor graphs.
    FactorGraph trainedModel = linearClassifier.getModelFromParameters(parameters)
        .getFactorGraph(DynamicAssignment.EMPTY);
            
    // Should be able to get 0 training error. This uses a simple wrapper around
    // the factor graph. Another option is to use
    // trainedModel.conditional(x.outcomeArrayToAssignment(<the feature vector)),
    // which computes the distribution over y given that x = feature vector.
    SimpleFactorGraphPredictor predictor = new SimpleFactorGraphPredictor(
        trainedModel, y, new JunctionTree());
    for (Example<Assignment, Assignment> trainingDatum : trainingData) {
      Assignment prediction = predictor.getBestPrediction(trainingDatum.getInput()).getBestPrediction();
      assertEquals(trainingDatum.getOutput(), prediction);
    }
  }
}
