package com.jayantkrish.jklol.training;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import com.google.common.base.Supplier;
import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.inference.MarginalCalculator;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.bayesnet.BayesNet;
import com.jayantkrish.jklol.models.bayesnet.BayesNetBuilder;
import com.jayantkrish.jklol.models.bayesnet.Cpt;
import com.jayantkrish.jklol.models.bayesnet.CptTableFactor;
import com.jayantkrish.jklol.util.Assignment;

public class EMTrainerTest extends TestCase {

	BayesNet bn;
	IncrementalEMTrainer t;
	List<Assignment> trainingData;
	Cpt actualNoNodeCpt;
	Cpt actualVCpt;
	Cpt expectedNoNodeCpt;
	Cpt expectedVCpt;
	
	CptTableFactor f0;
	CptTableFactor f1;
	CptTableFactor f2;

	Assignment a1,a2,a3,testAssignment1,testAssignment2;
	List<String> allVarNames;

	StepwiseEMTrainer s;

	public void setUp() {
		BayesNetBuilder builder = new BayesNetBuilder();

		DiscreteVariable tfVar = new DiscreteVariable("TrueFalse",
				Arrays.asList(new String[] {"T", "F"}));

		builder.addDiscreteVariable("Var0", tfVar);
		builder.addDiscreteVariable("Var1", tfVar);
		builder.addDiscreteVariable("Var2", tfVar);

		f0 = builder.addCptFactor(Collections.<String>emptyList(), Arrays.asList(new String[] {"Var0"}));
		f1 = builder.addCptFactor(Collections.<String>emptyList(), Arrays.asList(new String[] {"Var1"}));
		f2 = builder.addCptFactor(Arrays.asList(new String[] {"Var0", "Var1"}), 
				Arrays.asList(new String[] {"Var2"}));

		actualNoNodeCpt = new Cpt(Collections.<DiscreteVariable>emptyList(), Arrays.asList(new DiscreteVariable[] {tfVar}));
		actualVCpt = new Cpt(Arrays.asList(new DiscreteVariable[] {tfVar, tfVar}), Arrays.asList(new DiscreteVariable[] {tfVar}));

		Map<Integer, Integer> nodeCptMap0 = new HashMap<Integer, Integer>();
		nodeCptMap0.put(0, 0);
		Map<Integer, Integer> nodeCptMap1 = new HashMap<Integer, Integer>();
		nodeCptMap1.put(1, 0);
		Map<Integer, Integer> vCptMap = new HashMap<Integer, Integer>();
		vCptMap.put(0, 0);
		vCptMap.put(1, 1);
		vCptMap.put(2, 2);

		f0.setCpt(actualNoNodeCpt, nodeCptMap0);
		f1.setCpt(actualNoNodeCpt, nodeCptMap1);
		f2.setCpt(actualVCpt, vCptMap);

		allVarNames = Arrays.asList(new String[] {"Var0", "Var1", "Var2"});
		List<String> observedVarNames = Arrays.asList(new String[] {"Var0", "Var2"});

		bn = builder.build();		
		trainingData = new ArrayList<Assignment>();
		a1 = bn.outcomeToAssignment(observedVarNames,
				Arrays.asList(new String[] {"F", "T"}));
		a2 = bn.outcomeToAssignment(observedVarNames,
				Arrays.asList(new String[] {"T", "F"}));
		a3 = bn.outcomeToAssignment(observedVarNames,
				Arrays.asList(new String[] {"F", "F"}));
		for (int i = 0; i < 3; i++) {
			trainingData.add(a1);
			trainingData.add(a2);
			trainingData.add(a3);
		}

		// Creates the marginal calculators which are used in stepwise EM training.
		Supplier<MarginalCalculator> inferenceSupplier = new Supplier<MarginalCalculator>() {
		  public MarginalCalculator get() {
		    return new JunctionTree();
		  }
		};
		
		t = new IncrementalEMTrainer(10, 1.0, new JunctionTree());
		s = new StepwiseEMTrainer(10, 4, 1.0, 0.9, inferenceSupplier, 3, null);
		
		testAssignment1 = bn.outcomeToAssignment(allVarNames, Arrays.asList("T", "F", "F"));
		testAssignment2 = bn.outcomeToAssignment(allVarNames, Arrays.asList("F", "F", "T"));
	}

	public void testIncrementalEM() {
		t.train(bn, trainingData);

		assertEquals(0.8, f2.getUnnormalizedProbability(testAssignment1), 0.1);
		assertEquals(0.5, f2.getUnnormalizedProbability(testAssignment2), 0.05);
	}

	public void testStepwiseEM() {
		s.train(bn, trainingData);

		for (Factor factor : bn.getFactors()) {
		  System.out.println(factor);
		}
		
		// Stepwise EM loses the smoothing factor, hence the different expected value for this test.
		assertEquals(1.0, f2.getUnnormalizedProbability(testAssignment1), 0.05);
		assertEquals(0.5, f2.getUnnormalizedProbability(testAssignment2), 0.05);
	}
}
