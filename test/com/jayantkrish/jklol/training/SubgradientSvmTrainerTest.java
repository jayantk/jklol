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
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.loglinear.DiscreteLogLinearFactor;
import com.jayantkrish.jklol.models.loglinear.LogLinearModelBuilder;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
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
	
	List<Example<Assignment, Assignment>> trainingData;
	
	public void setUp() {
	  LogLinearModelBuilder builder = new LogLinearModelBuilder();
	  DiscreteVariable tfVar = new DiscreteVariable("TrueFalse",
	      Arrays.asList(new String[] {"T", "F"}));

	  builder.addDiscreteVariable("X0", tfVar);
		builder.addDiscreteVariable("X1", tfVar);
		builder.addDiscreteVariable("Y", tfVar);

		builder.addFactor(DiscreteLogLinearFactor
		    .createIndicatorFactor(builder.lookupVariables("X0", "Y")));
		builder.addFactor(DiscreteLogLinearFactor
		    .createIndicatorFactor(builder.lookupVariables("X1", "Y")));
		model = builder.build();
		
		inputVars = builder.lookupVariables("X0", "X1");
		outputVars = builder.lookupVariables("Y");
		
		trainingData = Lists.newArrayList();
		trainingData.add(Example.create(inputVars.outcomeArrayToAssignment("F", "F"),
		    outputVars.outcomeArrayToAssignment("T")));
		trainingData.add(Example.create(inputVars.outcomeArrayToAssignment("T", "F"),
		    outputVars.outcomeArrayToAssignment("T")));
		trainingData.add(Example.create(inputVars.outcomeArrayToAssignment("F", "T"),
		    outputVars.outcomeArrayToAssignment("F")));
		trainingData.add(Example.create(inputVars.outcomeArrayToAssignment("T", "T"),
		    outputVars.outcomeArrayToAssignment("F")));
		
		t = new SubgradientSvmTrainer(10, 4, 1.0, new JunctionTree(), 
		    new SubgradientSvmTrainer.HammingCost(), null);
	}
	
	public void testTrain() {
	  SufficientStatistics parameters = t.train(model, trainingData);
	  assertEquals(1.0, parameters.getL2Norm(), .01);
	  
	  FactorGraph factorGraph = model.getFactorGraphFromParameters(parameters);
	  for (Example<Assignment, Assignment> example : trainingData) {
	    Assignment a = example.getInput().union(example.getOutput());
	    assertEquals(0.5, factorGraph.getUnnormalizedLogProbability(a), .01);
	  }
	  
	  VariableNumMap vars = inputVars.union(outputVars);
	  Assignment incorrect = vars.outcomeArrayToAssignment("F", "F", "F");
	  assertEquals(-0.5, factorGraph.getUnnormalizedLogProbability(incorrect), .01);
	}
  
	public void testHammingCost() {
	  FactorGraph graph = model.getFactorGraphFromParameters(model.getNewSufficientStatistics());
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
}
