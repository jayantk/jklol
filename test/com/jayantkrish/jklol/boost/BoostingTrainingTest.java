package com.jayantkrish.jklol.boost;

import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.evaluation.Example;
import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.training.DefaultLogFunction;

/**
 * Regression tests for training a boosting classifier.
 * 
 * @author jayantk
 */
public class BoostingTrainingTest extends TestCase {
  
  ParametricFactorGraphEnsemble pfg;
  List<String> clique1Names;
  List<String> clique2Names;
  VariableNumMap allVariables;

  List<Example<DynamicAssignment, DynamicAssignment>> trainingData;
  	
  public void setUp() {
    DiscreteVariable tfVar = new DiscreteVariable("TrueFalse", Arrays.asList("F", "T"));

    ParametricFactorGraphEnsembleBuilder builder = new ParametricFactorGraphEnsembleBuilder();
    builder.addVariable("Var0", tfVar);
    builder.addVariable("Var1", tfVar);
    builder.addVariable("Var2", tfVar);
    builder.addVariable("Var3", tfVar);
    allVariables = builder.getVariables();

    clique1Names = Arrays.asList("Var0", "Var1", "Var2");
    builder.addUnreplicatedFactor("f0", new AveragingBoostingFamily(
        builder.getVariables().getVariablesByName(clique1Names)));

    clique2Names = Arrays.asList("Var2", "Var3");
    builder.addUnreplicatedFactor("f1", new AveragingBoostingFamily(
        builder.getVariables().getVariablesByName(clique2Names)));

    pfg = builder.build();
    trainingData = Lists.newArrayList();
    DynamicAssignment a1 = pfg.getVariables()
        .fixedVariableOutcomeToAssignment(Arrays.asList("T", "T", "T", "T"));
    DynamicAssignment a2 = pfg.getVariables()
        .fixedVariableOutcomeToAssignment(Arrays.asList("T", "T", "T", "F"));
    DynamicAssignment a3 = pfg.getVariables()
        .fixedVariableOutcomeToAssignment(Arrays.asList("F", "F", "F", "F"));
    for (int i = 0; i < 3; i++) {
      trainingData.add(Example.create(DynamicAssignment.EMPTY, a1));
      trainingData.add(Example.create(DynamicAssignment.EMPTY, a2));
      trainingData.add(Example.create(DynamicAssignment.EMPTY, a3));
    }
  }
  
  public void testTrain() {
    FunctionalGradientAscent ascent = new FunctionalGradientAscent(100, 3, 0.01, true, new DefaultLogFunction());
    LoglikelihoodBoostingOracle oracle = new LoglikelihoodBoostingOracle(pfg, new JunctionTree());
    
    SufficientStatisticsEnsemble ensemble = ascent.train(oracle, pfg.getNewSufficientStatistics(), trainingData);
    
    FactorGraph fg = pfg.getModelFromParameters(ensemble).conditional(DynamicAssignment.EMPTY);
    System.out.println(fg.getParameterDescription());
  }
}
