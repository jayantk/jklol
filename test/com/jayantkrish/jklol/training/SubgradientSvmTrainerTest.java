package com.jayantkrish.jklol.training;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.evaluation.Example;
import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.loglinear.DiscreteLogLinearFactor;
import com.jayantkrish.jklol.models.loglinear.IndicatorLogLinearFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraphBuilder;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.SubgradientSvmTrainer.HammingCost;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Test cases for {@link SubgradientSvmTrainer}.
 * 
 * @author jayantk
 */
public class SubgradientSvmTrainerTest extends TestCase {

  ParametricFactorGraph model;
  SubgradientSvmTrainer t;    

  VariableNumMap inputVars, outputVars;

  List<Example<DynamicAssignment, DynamicAssignment>> trainingData;

  public void setUp() {
    ParametricFactorGraphBuilder builder = new ParametricFactorGraphBuilder();
    DiscreteVariable tfVar = new DiscreteVariable("TrueFalse",
        Arrays.asList(new String[] {"T", "F"}));

    builder.addVariable("X0", tfVar);
    builder.addVariable("X1", tfVar);
    builder.addVariable("Y", tfVar);

		VariableNumMap factorVariables = builder.getVariables().getVariablesByName("X0", "Y");
		builder.addUnreplicatedFactor("f0", new IndicatorLogLinearFactor(factorVariables,
		    TableFactor.unity(factorVariables)));
    
    builder.addUnreplicatedFactor("f1", DiscreteLogLinearFactor
        .createIndicatorFactor(builder.getVariables().getVariablesByName("X1", "Y")));
    model = builder.build();

    inputVars = builder.getVariables().getVariablesByName("X0", "X1");
    outputVars = builder.getVariables().getVariablesByName("Y");

    trainingData = Lists.newArrayList();
    trainingData.add(makeExample("F", "F", "T"));
    trainingData.add(makeExample("T", "F", "T"));
    trainingData.add(makeExample("F", "T", "F"));
    trainingData.add(makeExample("T", "T", "F"));

    t = new SubgradientSvmTrainer(new JunctionTree(), 
        new SubgradientSvmTrainer.HammingCost(), 100, 4, 1.0, true, 1.0, null);
  }

  public void testTrain() {
    SufficientStatistics parameters = t.train(model, model.getNewSufficientStatistics(), trainingData);
    assertEquals(1.0, parameters.getL2Norm(), .01);

    DynamicFactorGraph dynamicFactorGraph = model.getFactorGraphFromParameters(parameters);
    FactorGraph factorGraph = dynamicFactorGraph.getFactorGraph(DynamicAssignment.EMPTY);
    for (Example<DynamicAssignment, DynamicAssignment> example : trainingData) {
      Assignment input = dynamicFactorGraph.getVariables().toAssignment(example.getInput());
      Assignment output = dynamicFactorGraph.getVariables().toAssignment(example.getOutput());
      assertEquals(0.5, factorGraph.getUnnormalizedLogProbability(input.union(output)), .01);
    }

    VariableNumMap vars = inputVars.union(outputVars);
    Assignment incorrect = vars.outcomeArrayToAssignment("F", "F", "F");
    assertEquals(-0.5, factorGraph.getUnnormalizedLogProbability(incorrect), .01);
  }

  public void testHammingCost() {
    FactorGraph graph = model.getFactorGraphFromParameters(model.getNewSufficientStatistics()).getFactorGraph(DynamicAssignment.EMPTY);
    HammingCost cost = new HammingCost();
    FactorGraph augmented = cost.augmentWithCosts(graph, outputVars, outputVars.outcomeArrayToAssignment("F"));
    assertEquals(3, augmented.getFactors().size());

    Set<Factor> addedFactors = Sets.newHashSet(augmented.getFactors());
    addedFactors.removeAll(graph.getFactors());
    Factor addedFactor = addedFactors.iterator().next();
    assertEquals(outputVars, addedFactor.getVars());
    assertEquals(Math.E, addedFactor.getUnnormalizedProbability(outputVars.outcomeArrayToAssignment("T")));
    assertEquals(1.0, addedFactor.getUnnormalizedProbability(outputVars.outcomeArrayToAssignment("F")));
  }

  private Example<DynamicAssignment, DynamicAssignment> makeExample(String x0, String x1, String y) {
    return Example.create(DynamicAssignment.fromAssignment(inputVars.outcomeArrayToAssignment(x0, x1)),
        DynamicAssignment.fromAssignment(outputVars.outcomeArrayToAssignment(y)));  
  }
}