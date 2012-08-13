package com.jayantkrish.jklol.training;

import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.evaluation.Example;
import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.bayesnet.CptTableFactor;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraphBuilder;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.util.Assignment;

public class EMTrainerTest extends TestCase {

  ParametricFactorGraph bn;
  List<Example<Assignment, Assignment>> trainingData;

  CptTableFactor f0;
  CptTableFactor f1;

  Assignment a1,a2,a3,a4,testAssignment1,testAssignment2, zeroProbAssignment;
  VariableNumMap allVars;

  Trainer<ParametricFactorGraph, Example<Assignment, Assignment>> t, s, e;

  public void setUp() {
    ParametricFactorGraphBuilder builder = new ParametricFactorGraphBuilder();

    DiscreteVariable tfVar = new DiscreteVariable("TrueFalse",
        Arrays.asList(new String[] {"T", "F"}));

    builder.addVariable("Var0", tfVar);
    builder.addVariable("Var1", tfVar);

    VariableNumMap var0 = builder.getVariables().getVariablesByName("Var0");
    VariableNumMap var1 = builder.getVariables().getVariablesByName("Var1");
    allVars = var0.union(var1);
    
    f0 = new CptTableFactor(VariableNumMap.emptyMap(), var0);
    builder.addUnreplicatedFactor("f0", f0);
    f1 = new CptTableFactor(var0, var1);
    builder.addUnreplicatedFactor("f1", f1);

    bn = builder.build();		
    trainingData = Lists.newArrayList();
    a1 = var1.outcomeArrayToAssignment("F");
    a2 = allVars.outcomeArrayToAssignment("T", "T");
    a3 = allVars.outcomeArrayToAssignment("F", "F");
    a4 = var1.outcomeArrayToAssignment("T");

    for (int i = 0; i < 3; i++) {
      trainingData.add(Example.create(Assignment.EMPTY, a1));
    }
    trainingData.add(Example.create(Assignment.EMPTY, a4));
    trainingData.add(Example.create(Assignment.EMPTY, a2));
    trainingData.add(Example.create(Assignment.EMPTY, a3));

    t = TrainerAdapter.createAssignmentAdapter(new IncrementalEMTrainer(10, new JunctionTree()));
    s = TrainerAdapter.createAssignmentAdapter(new StepwiseEMTrainer(10, 4, 0.9, new JunctionTree(), null));
    e = TrainerAdapter.createAssignmentAdapter(new EMTrainer(20, new JunctionTree(), null));

    testAssignment1 = allVars.outcomeArrayToAssignment("T", "T");
    testAssignment2 = allVars.outcomeArrayToAssignment("F", "F");
    zeroProbAssignment = allVars.outcomeArrayToAssignment("F", "T");
  }

  public void testIncrementalEM() {
    Factor factor = trainBayesNet(t);
    assertEquals(8.0 / 14.0, factor.getUnnormalizedProbability(testAssignment1), 0.05);
    assertEquals(12.0 / 16.0, factor.getUnnormalizedProbability(testAssignment2), 0.05);
  }

  public void testStepwiseEM() {
    Factor factor = trainBayesNet(s);
    // Numbers calculated from 1 iteration of EM, assuming smoothing disappears. The T->T number is fudged a bit.
    // Stepwise EM loses the smoothing factor, hence the different expected value for this test.
    assertEquals(7.0 / 10.0, factor.getUnnormalizedProbability(testAssignment1), 0.1);
    assertEquals(9.0 / 10.0, factor.getUnnormalizedProbability(testAssignment2), 0.05);
  }

  public void testEM() {		
    Factor factor = trainBayesNet(e);
    assertEquals(8.0 / 14.0, factor.getUnnormalizedProbability(testAssignment1), 0.05);
    assertEquals(12.0 / 16.0, factor.getUnnormalizedProbability(testAssignment2), 0.05);
  }
  
  private Factor trainBayesNet(
      Trainer<ParametricFactorGraph, Example<Assignment, Assignment>> trainer) {
    SufficientStatistics initialParameters = bn.getNewSufficientStatistics();
    initialParameters.increment(1.0);
    SufficientStatistics trainedParameters = trainer.train(bn, initialParameters, trainingData);

    FactorGraph factorGraph = bn.getFactorGraphFromParameters(trainedParameters)
        .getFactorGraph(DynamicAssignment.EMPTY);		
    return factorGraph.getFactors().get(1);
  }

  public void testRetainSparsityIncrementalEM() {
    Factor factor = trainBayesNetSparse(t);
    assertEquals(0.0, factor.getUnnormalizedProbability(zeroProbAssignment), 0.01);
    assertEquals(1.0, factor.getUnnormalizedProbability(testAssignment2), 0.01);
  }

  public void testRetainSparsityStepwiseEM() {
    Factor factor = trainBayesNetSparse(s);
    assertEquals(0.0, factor.getUnnormalizedProbability(zeroProbAssignment), 0.01);
    assertEquals(1.0, factor.getUnnormalizedProbability(testAssignment2), 0.01);
  }

  public void testRetainSparsityEM() {
    Factor factor = trainBayesNetSparse(e);
    assertEquals(0.0, factor.getUnnormalizedProbability(zeroProbAssignment), 0.01);
    assertEquals(1.0, factor.getUnnormalizedProbability(testAssignment2), 0.01);
  }
      
  private Factor trainBayesNetSparse(Trainer<ParametricFactorGraph, Example<Assignment, Assignment>> trainer) {
    //  If parameters are initialized sparsely, the sparsity should be retained throughout all algorithms.
    Assignment probAssignment = allVars.outcomeArrayToAssignment("F", "F");
    SufficientStatistics initialParameters = bn.getNewSufficientStatistics();
    initialParameters.increment(1.0);
    bn.incrementSufficientStatistics(initialParameters, allVars, zeroProbAssignment, -1.0);
    bn.incrementSufficientStatistics(initialParameters, allVars, probAssignment, 1.0);

    SufficientStatistics trainedParameters = trainer.train(bn, initialParameters, trainingData);
    FactorGraph factorGraph = bn.getFactorGraphFromParameters(trainedParameters)
        .getFactorGraph(DynamicAssignment.EMPTY);
    return factorGraph.getFactors().get(1);
  }
}
