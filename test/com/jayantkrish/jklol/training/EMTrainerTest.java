package com.jayantkrish.jklol.training;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import junit.framework.TestCase;

import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.bayesnet.BayesNetBuilder;
import com.jayantkrish.jklol.models.bayesnet.CptTableFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.util.Assignment;

public class EMTrainerTest extends TestCase {

	ParametricFactorGraph bn;
	IncrementalEMTrainer t;
	List<Assignment> trainingData;
	
	CptTableFactor f0;
	CptTableFactor f1;

	Assignment a1,a2,a3,a4,testAssignment1,testAssignment2;
	List<String> allVarNames;

	StepwiseEMTrainer s;
	EMTrainer e;

	public void setUp() {
		BayesNetBuilder builder = new BayesNetBuilder();

		DiscreteVariable tfVar = new DiscreteVariable("TrueFalse",
				Arrays.asList(new String[] {"T", "F"}));

		builder.addDiscreteVariable("Var0", tfVar);
		builder.addDiscreteVariable("Var1", tfVar);

		f0 = builder.addCptFactorWithNewCpt(Collections.<String>emptyList(), Arrays.asList("Var0"));
		f1 = builder.addCptFactorWithNewCpt(Arrays.asList("Var0"), Arrays.asList("Var1"));

		allVarNames = Arrays.asList(new String[] {"Var0", "Var1"});
		List<String> observedVarNames = Arrays.asList(new String[] {"Var1"});

		bn = builder.build();		
		trainingData = new ArrayList<Assignment>();
		a1 = bn.getVariables().getVariablesByName(observedVarNames).outcomeToAssignment(
				Arrays.asList(new String[] {"F"}));
		a2 = bn.getVariables().getVariablesByName(allVarNames).outcomeToAssignment(Arrays.asList("T", "T"));
		a3 = bn.getVariables().getVariablesByName(allVarNames).outcomeToAssignment(Arrays.asList("F", "F"));
		a4 = bn.getVariables().getVariablesByName(Arrays.asList("Var1")).outcomeToAssignment(Arrays.asList("T"));

		for (int i = 0; i < 3; i++) {
			trainingData.add(a1);
		}
		trainingData.add(a4);
		trainingData.add(a2);
		trainingData.add(a3);
		
		t = new IncrementalEMTrainer(10, new JunctionTree());
		s = new StepwiseEMTrainer(10, 4, 0.9, new JunctionTree(), null);
		e = new EMTrainer(20, new JunctionTree(), null);
		
		testAssignment1 = bn.getVariables().getVariablesByName(allVarNames).outcomeToAssignment(Arrays.asList("T", "T"));
		testAssignment2 = bn.getVariables().getVariablesByName(allVarNames).outcomeToAssignment(Arrays.asList("F", "F"));
	}

	public void testIncrementalEM() {
	  SufficientStatistics initialParameters = bn.getNewSufficientStatistics();
	  initialParameters.increment(1.0);
		SufficientStatistics trainedParameters = t.train(bn, initialParameters, trainingData);
		
    // Numbers here calculated from running 1 iteration of EM on paper.
		FactorGraph factorGraph = bn.getFactorGraphFromParameters(trainedParameters);
		Factor factor = factorGraph.getFactors().get(1);
		assertEquals(8.0 / 14.0, factor.getUnnormalizedProbability(testAssignment1), 0.05);
		assertEquals(12.0 / 16.0, factor.getUnnormalizedProbability(testAssignment2), 0.05);
	}

	public void testStepwiseEM() {
	  SufficientStatistics initialParameters = bn.getNewSufficientStatistics();
	  initialParameters.increment(1.0);
		SufficientStatistics trainedParameters = s.train(bn, initialParameters, trainingData);
		
		FactorGraph factorGraph = bn.getFactorGraphFromParameters(trainedParameters);
		Factor factor = factorGraph.getFactors().get(1);
		// Numbers calculated from 1 iteration of EM, assuming smoothing disappears. The T->T number is fudged a bit.
		// Stepwise EM loses the smoothing factor, hence the different expected value for this test.
		assertEquals(7.0 / 10.0, factor.getUnnormalizedProbability(testAssignment1), 0.1);
		assertEquals(9.0 / 10.0, factor.getUnnormalizedProbability(testAssignment2), 0.05);
	}
	
	public void testEM() {
	  SufficientStatistics initialParameters = bn.getNewSufficientStatistics();
	  initialParameters.increment(1.0);
		SufficientStatistics trainedParameters = e.train(bn, initialParameters, trainingData);
		
		FactorGraph factorGraph = bn.getFactorGraphFromParameters(trainedParameters);		
		Factor factor = factorGraph.getFactors().get(1);
		assertEquals(8.0 / 14.0, factor.getUnnormalizedProbability(testAssignment1), 0.05);
		assertEquals(12.0 / 16.0, factor.getUnnormalizedProbability(testAssignment2), 0.05);
	}
	
	public void testRetainSparsityIncrementalEM() {
	  // If parameters are initialized sparsely, the sparsity should be retained throughout both algorithms.
	  Assignment zeroProbAssignment = bn.getVariables().outcomeToAssignment(Arrays.asList("F", "T"));
	  Assignment probAssignment = bn.getVariables().outcomeToAssignment(Arrays.asList("F", "F"));
	  SufficientStatistics initialParameters = bn.getNewSufficientStatistics();
	  initialParameters.increment(1.0);
	  bn.incrementSufficientStatistics(initialParameters, zeroProbAssignment, -1.0);
	  bn.incrementSufficientStatistics(initialParameters, probAssignment, 1.0);

	  SufficientStatistics trainedParameters = t.train(bn, initialParameters, trainingData);
	  FactorGraph factorGraph = bn.getFactorGraphFromParameters(trainedParameters);
		Factor factor = factorGraph.getFactors().get(1);
		
		assertEquals(0.0, factor.getUnnormalizedProbability(zeroProbAssignment), 0.01);
	  assertEquals(1.0, factor.getUnnormalizedProbability(testAssignment2), 0.01);
	}
	
	public void testRetainSparsityStepwiseEM() {
	  // If parameters are initialized sparsely, the sparsity should be retained throughout both algorithms.
	  Assignment zeroProbAssignment = bn.getVariables().outcomeToAssignment(Arrays.asList("F", "T"));
	  Assignment probAssignment = bn.getVariables().outcomeToAssignment(Arrays.asList("F", "F"));
	  SufficientStatistics initialParameters = bn.getNewSufficientStatistics();
	  initialParameters.increment(1.0);
	  bn.incrementSufficientStatistics(initialParameters, zeroProbAssignment, -1.0);
	  bn.incrementSufficientStatistics(initialParameters, probAssignment, 1.0);

	  SufficientStatistics trainedParameters = s.train(bn, initialParameters, trainingData);
	  FactorGraph factorGraph = bn.getFactorGraphFromParameters(trainedParameters);
		Factor factor = factorGraph.getFactors().get(1);
		
		assertEquals(0.0, factor.getUnnormalizedProbability(zeroProbAssignment), 0.01);
	  assertEquals(1.0, factor.getUnnormalizedProbability(testAssignment2), 0.01);
	}
	
	public void testRetainSparsityEM() {
	  // If parameters are initialized sparsely, the sparsity should be retained throughout.
	  Assignment zeroProbAssignment = bn.getVariables().outcomeToAssignment(Arrays.asList("F", "T"));
	  Assignment probAssignment = bn.getVariables().outcomeToAssignment(Arrays.asList("F", "F"));
	  SufficientStatistics initialParameters = bn.getNewSufficientStatistics();
	  initialParameters.increment(1.0);
	  bn.incrementSufficientStatistics(initialParameters, zeroProbAssignment, -1.0);
	  bn.incrementSufficientStatistics(initialParameters, probAssignment, 1.0);

	  SufficientStatistics trainedParameters = e.train(bn, initialParameters, trainingData);
	  FactorGraph factorGraph = bn.getFactorGraphFromParameters(trainedParameters);
		Factor factor = factorGraph.getFactors().get(1);
		
		assertEquals(0.0, factor.getUnnormalizedProbability(zeroProbAssignment), 0.01);
	  assertEquals(1.0, factor.getUnnormalizedProbability(testAssignment2), 0.01);
	}
}
