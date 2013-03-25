package com.jayantkrish.jklol.boost;

import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.dtree.RegressionTreeTrainer;
import com.jayantkrish.jklol.evaluation.Example;
import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.ObjectVariable;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.dynamic.VariableNamePattern;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.training.DefaultLogFunction;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Regression tests for training a boosting classifier.
 * 
 * @author jayantk
 */
public class BoostingTrainingTest extends TestCase {
  
  ParametricFactorGraphEnsemble sequenceModel, classifierModel, classifierModel2;
  VariableNumMap x, y, all;
  
  List<Example<DynamicAssignment, DynamicAssignment>> classifierTrainingData;
  List<Example<DynamicAssignment, DynamicAssignment>> sequenceTrainingData;
  
  List<Example<DynamicAssignment, DynamicAssignment>> classifierTestData;
  List<Example<DynamicAssignment, DynamicAssignment>> sequenceTestData;
  List<DynamicAssignment> classifierEqualProbData;
  
  public void setUp() {
    ParametricFactorGraphEnsembleBuilder sequenceModelBuilder = new ParametricFactorGraphEnsembleBuilder();
    ParametricFactorGraphEnsembleBuilder classifierModelBuilder = new ParametricFactorGraphEnsembleBuilder();
    ParametricFactorGraphEnsembleBuilder classifierModel2Builder = new ParametricFactorGraphEnsembleBuilder();
    // Create a plate for each input/output pair.
    DiscreteVariable outputVar = new DiscreteVariable("tf", Arrays.asList("F", "T"));
    ObjectVariable tensorVar = new ObjectVariable(Tensor.class);
    DiscreteVariable featureVar = DiscreteVariable.sequence("foo", 4);
    sequenceModelBuilder.addPlate("plateVar", new VariableNumMap(Ints.asList(0, 1), 
        Arrays.asList("x", "y"), Arrays.<Variable>asList(tensorVar, outputVar)), 10);
    classifierModelBuilder.addPlate("plateVar", new VariableNumMap(Ints.asList(0, 1), 
        Arrays.asList("x", "y"), Arrays.<Variable>asList(tensorVar, outputVar)), 10);
    classifierModel2Builder.addPlate("plateVar", new VariableNumMap(Ints.asList(0, 1), 
        Arrays.asList("x", "y"), Arrays.<Variable>asList(tensorVar, outputVar)), 10);

    // Factor connecting each x to the corresponding y.
    all = new VariableNumMap(Ints.asList(0, 1), 
        Arrays.asList("plateVar/?(0)/x", "plateVar/?(0)/y"), Arrays.<Variable>asList(tensorVar, outputVar));
    x = all.getVariablesByName("plateVar/?(0)/x");
    y = all.getVariablesByName("plateVar/?(0)/y");
    RegressionTreeBoostingFamily f = new RegressionTreeBoostingFamily(x, y, new RegressionTreeTrainer(1), 
        featureVar, TableFactor.unity(y).getWeights());
    RegressionTreeBoostingFamily f2 = new RegressionTreeBoostingFamily(x, y, new RegressionTreeTrainer(2), 
        featureVar, TableFactor.unity(y).getWeights());
    sequenceModelBuilder.addFactor("classifier", f, VariableNamePattern.fromTemplateVariables(all, VariableNumMap.emptyMap()));
    classifierModelBuilder.addFactor("classifier", f, VariableNamePattern.fromTemplateVariables(all, VariableNumMap.emptyMap()));
    classifierModel2Builder.addFactor("classifier", f2, VariableNamePattern.fromTemplateVariables(all, VariableNumMap.emptyMap()));

    // Factor connecting adjacent y's
    VariableNumMap adjacentVars = new VariableNumMap(Ints.asList(0, 1), 
        Arrays.asList("plateVar/?(0)/y", "plateVar/?(1)/y"), Arrays.asList(outputVar, outputVar));
    sequenceModelBuilder.addFactor("adjacent", new AveragingBoostingFamily(adjacentVars),
        VariableNamePattern.fromTemplateVariables(adjacentVars, VariableNumMap.emptyMap()));    

    sequenceModel = sequenceModelBuilder.build();
    classifierModel = classifierModelBuilder.build();
    classifierModel2 = classifierModel2Builder.build();
        
    // Construct some training data.
    List<Assignment> inputAssignments = Lists.newArrayList();
    for (int i = 0; i < 8; i++) {
      double[] values = new double[4];
      values[0] = (i % 2) * 2;
      values[1] = ((i / 2) % 2) * 2;
      values[2] = ((i / 4) % 2) * 2;
      values[3] = 1;
      inputAssignments.add(x.outcomeArrayToAssignment(SparseTensor.vector(0, 4, values)));
    }
    
    Assignment yf = y.outcomeArrayToAssignment("F");
    Assignment yt = y.outcomeArrayToAssignment("T");
    
    classifierTrainingData = Lists.newArrayList();
    classifierTrainingData.add(getListVarAssignment(Arrays.asList(inputAssignments.get(0)), Arrays.asList(yf)));
    classifierTrainingData.add(getListVarAssignment(Arrays.asList(inputAssignments.get(0)), Arrays.asList(yf)));
    classifierTrainingData.add(getListVarAssignment(Arrays.asList(inputAssignments.get(1)), Arrays.asList(yf)));
    //classifierTrainingData.add(getListVarAssignment(Arrays.asList(inputAssignments.get(2)), Arrays.asList(yf)));
    classifierTrainingData.add(getListVarAssignment(Arrays.asList(inputAssignments.get(3)), Arrays.asList(yt)));
    classifierTrainingData.add(getListVarAssignment(Arrays.asList(inputAssignments.get(3)), Arrays.asList(yt)));
    //classifierTrainingData.add(getListVarAssignment(Arrays.asList(inputAssignments.get(1)), Arrays.asList(yt)));
    classifierTrainingData.add(getListVarAssignment(Arrays.asList(inputAssignments.get(2)), Arrays.asList(yt)));
    
    classifierTestData = Lists.newArrayList();
    classifierTestData.add(getListVarAssignment(Arrays.asList(inputAssignments.get(0)), Arrays.asList(yf)));
    classifierTestData.add(getListVarAssignment(Arrays.asList(inputAssignments.get(4)), Arrays.asList(yf)));
    classifierTestData.add(getListVarAssignment(Arrays.asList(inputAssignments.get(3)), Arrays.asList(yt)));
    classifierTestData.add(getListVarAssignment(Arrays.asList(inputAssignments.get(7)), Arrays.asList(yt)));
    
    classifierEqualProbData = Lists.newArrayList();
    /*
    classifierEqualProbData.add(DynamicAssignment.createPlateAssignment("plateVar",
        Arrays.asList(inputAssignments.get(1))));
    classifierEqualProbData.add(DynamicAssignment.createPlateAssignment("plateVar",
        Arrays.asList(inputAssignments.get(2))));
        */

    sequenceTrainingData = Lists.newArrayList();
    sequenceTrainingData.add(getListVarAssignment(Arrays.asList(inputAssignments.get(3),
        inputAssignments.get(5)), Arrays.asList(yt, yt)));
    sequenceTrainingData.add(getListVarAssignment(Arrays.asList(inputAssignments.get(0),
        inputAssignments.get(2)), Arrays.asList(yf, yf)));
    
    sequenceTestData = Lists.newArrayList();
    sequenceTestData.addAll(sequenceTrainingData);
    // sequenceTestData.add(getListVarAssignment(Arrays.asList(inputAssignments.)))
  }
  
  private Example<DynamicAssignment, DynamicAssignment> getListVarAssignment(
      List<Assignment> xs, List<Assignment> ys) {
    Preconditions.checkArgument(xs.size() == ys.size());
    DynamicAssignment input = DynamicAssignment.createPlateAssignment("plateVar", xs);
    DynamicAssignment output = DynamicAssignment.createPlateAssignment("plateVar", ys);
    return Example.create(input, output); 
  }
  
  public void testBoostDecisionStump() {
    runClassifierTest(classifierModel);
  }
  
  public void testBoostRegressionTree() {
    runClassifierTest(classifierModel2);
  }

  private void runClassifierTest(ParametricFactorGraphEnsemble pfg) {
    FunctionalGradientAscent ascent = new FunctionalGradientAscent(10, classifierTrainingData.size(),
        1.0, true, new DefaultLogFunction());
    LoglikelihoodBoostingOracle oracle = new LoglikelihoodBoostingOracle(pfg, new JunctionTree());
    
    SufficientStatisticsEnsemble ensemble = ascent.train(oracle,
        pfg.getNewSufficientStatistics(), classifierTrainingData);
    
    JunctionTree jt = new JunctionTree();
    DynamicFactorGraph fg = pfg.getModelFromParameters(ensemble);
    for (Example<DynamicAssignment, DynamicAssignment> example : classifierTestData) {
      Assignment predicted = jt.computeMaxMarginals(fg.conditional(example.getInput())).getNthBestAssignment(0);
      Assignment trueOutput = fg.getVariables().toAssignment(example.getOutput());
      assertEquals(trueOutput, predicted.intersection(trueOutput.getVariableNums()));
    }
    
    // Verify the probability distribution
    /*
    for (DynamicAssignment assignment : classifierEqualProbData) {
      DiscreteFactor factor = fg.conditional(assignment).getFactors().get(0).coerceToDiscrete();
      double partitionFunction = factor.getTotalUnnormalizedProbability();
      System.out.println(factor.product(1.0 / partitionFunction).describeAssignments(factor.getMostLikelyAssignments(2)));
    }
    */
    
    System.out.println(pfg.getParameterDescription(ensemble));
  }
  
  public void testTrain() {
    FunctionalGradientAscent ascent = new FunctionalGradientAscent(10, sequenceTrainingData.size(),
        1.0, true, new DefaultLogFunction());
    LoglikelihoodBoostingOracle oracle = new LoglikelihoodBoostingOracle(sequenceModel, new JunctionTree());
    
    SufficientStatisticsEnsemble ensemble = ascent.train(oracle,
        sequenceModel.getNewSufficientStatistics(), sequenceTrainingData);
    System.out.println(sequenceModel.getParameterDescription(ensemble));
    
    JunctionTree jt = new JunctionTree();
    DynamicFactorGraph fg = sequenceModel.getModelFromParameters(ensemble);
    for (Example<DynamicAssignment, DynamicAssignment> example : sequenceTestData) {
      Assignment predicted = jt.computeMaxMarginals(fg.conditional(example.getInput())).getNthBestAssignment(0);
      Assignment trueOutput = fg.getVariables().toAssignment(example.getOutput());
      assertEquals(trueOutput, predicted.intersection(trueOutput.getVariableNums()));
    }

    /*
    FactorGraph fg = sequenceModel.getModelFromParameters(ensemble).conditional(sequenceTrainingData.get(0).getInput());
    System.out.println(fg.getParameterDescription());
    fg = sequenceModel.getModelFromParameters(ensemble).conditional(sequenceTrainingData.get(1).getInput());
    System.out.println(fg.getParameterDescription());
    */
  }
}
