package com.jayantkrish.jklol.training;

import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.evaluation.Example;
import com.jayantkrish.jklol.evaluation.FactorGraphPredictor;
import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.ObjectVariable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.dynamic.VariableNamePattern;
import com.jayantkrish.jklol.models.loglinear.ConditionalLogLinearFactor;
import com.jayantkrish.jklol.models.loglinear.DiscreteLogLinearFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraphBuilder;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Regression test for training and predicting with sequence models.
 * 
 * @author jayantk
 */
public class SequenceModelTest extends TestCase {
  ParametricFactorGraph sequenceModel;
  VariableNumMap x, y, all;
  List<Example<DynamicAssignment, DynamicAssignment>> trainingData;

  public void setUp() {
    ParametricFactorGraphBuilder builder = new ParametricFactorGraphBuilder();

    // Create a plate for each input/output pair.
    DiscreteVariable outputVar = new DiscreteVariable("tf",
        Arrays.asList("T", "F"));
    ObjectVariable tensorVar = new ObjectVariable(Tensor.class);
    builder.addPlate("plateVar", new VariableNumMap(Ints.asList(0, 1), 
        Arrays.asList("x", "y"), Arrays.asList(tensorVar, outputVar)), 10);

    // Factor connecting each x to the corresponding y.
    all = new VariableNumMap(Ints.asList(0, 1), 
        Arrays.asList("plateVar/?(0)/x", "plateVar/?(0)/y"), Arrays.asList(tensorVar, outputVar));
    x = all.getVariablesByName("plateVar/?(0)/x");
    y = all.getVariablesByName("plateVar/?(0)/y");
    ConditionalLogLinearFactor f = new ConditionalLogLinearFactor(x, y, 4, DenseTensorBuilder.getFactory());
    builder.addFactor("classifier", f, VariableNamePattern.fromTemplateVariables(all, VariableNumMap.emptyMap()));

    // Factor connecting adjacent y's
    VariableNumMap adjacentVars = new VariableNumMap(Ints.asList(0, 1), 
        Arrays.asList("plateVar/?(0)/y", "plateVar/?(1)/y"), Arrays.asList(outputVar, outputVar));
    builder.addFactor("adjacent", DiscreteLogLinearFactor.createIndicatorFactor(adjacentVars),
        VariableNamePattern.fromTemplateVariables(adjacentVars, VariableNumMap.emptyMap()));

    sequenceModel = builder.build();
        
    // Construct some training data.
    List<Assignment> inputAssignments = Lists.newArrayList();
    for (int i = 0; i < 8; i++) {
      double[] values = new double[4];
      values[0] = (i % 2) * 2 - 1;
      values[1] = ((i / 2) % 2) * 2 - 1;
      values[2] = ((i / 4) % 2) * 2 - 1;
      values[3] = 1;
      inputAssignments.add(x.outcomeArrayToAssignment(SparseTensor.vector(0, 4, values)));
    }
    
    Assignment yf = y.outcomeArrayToAssignment("F");
    Assignment yt = y.outcomeArrayToAssignment("T");
    
    trainingData = Lists.newArrayList();
    trainingData.add(getListVarAssignment(Arrays.asList(inputAssignments.get(3), inputAssignments.get(5)),
        Arrays.asList(yt, yt)));
    trainingData.add(getListVarAssignment(Arrays.asList(inputAssignments.get(0), inputAssignments.get(2)),
        Arrays.asList(yf, yf)));
  }
  
  private Example<DynamicAssignment, DynamicAssignment> getListVarAssignment(
      List<Assignment> xs, List<Assignment> ys) {
    Preconditions.checkArgument(xs.size() == ys.size());
    DynamicAssignment input = DynamicAssignment.createPlateAssignment("plateVar", xs);
    DynamicAssignment output = DynamicAssignment.createPlateAssignment("plateVar", ys);
    return Example.create(input, output); 
  }
  
  public void testTrainSvm() {
    DefaultLogFunction logFn = new DefaultLogFunction();
    testZeroTrainingError(new SubgradientSvmTrainer(80, 1, 1.0, new JunctionTree(),
        new SubgradientSvmTrainer.HammingCost(), logFn));
    logFn.printTimeStatistics();
  }
  
  public void testTrainLogLinear() {
    testZeroTrainingError(new StochasticGradientTrainer(new JunctionTree(), 80, new DefaultLogFunction(), 1.0, 0.0));
  }
  
  public void testSerialization() {
    ParametricFactorGraph copy = ParametricFactorGraph.fromProto(sequenceModel.toProto());
    
    assertEquals(sequenceModel.getVariables(), copy.getVariables());
  }
  
  private void testZeroTrainingError(Trainer trainer) {
    SufficientStatistics parameters = trainer.train(sequenceModel, sequenceModel.getNewSufficientStatistics(), trainingData); 
    DynamicFactorGraph trainedModel = sequenceModel.getFactorGraphFromParameters(parameters);
    
    // Should be able to get 0 training error.
    FactorGraphPredictor predictor = new FactorGraphPredictor(trainedModel, 
        VariableNamePattern.fromTemplateVariables(y, VariableNumMap.emptyMap()), new JunctionTree());
    for (Example<DynamicAssignment, DynamicAssignment> trainingDatum : trainingData) {
      DynamicAssignment prediction = predictor.getBestPrediction(trainingDatum.getInput()).getBestPrediction();
      assertEquals(trainingDatum.getOutput(), prediction);
    }
  }
}
