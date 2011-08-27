package com.jayantkrish.jklol.training;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import junit.framework.TestCase;

import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.models.DiscreteVariable;
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

	Assignment a1,a2,a3,a4,testAssignment1,testAssignment2;
	List<String> allVarNames;

	StepwiseEMTrainer s;

	public void setUp() {
		BayesNetBuilder builder = new BayesNetBuilder();

		DiscreteVariable tfVar = new DiscreteVariable("TrueFalse",
				Arrays.asList(new String[] {"T", "F"}));

		builder.addDiscreteVariable("Var0", tfVar);
		builder.addDiscreteVariable("Var1", tfVar);
		builder.addDiscreteVariable("Var2", tfVar);

		f0 = builder.addCptFactorWithNewCpt(Collections.<String>emptyList(), Arrays.asList("Var0"));
		f1 = builder.addCptFactorWithNewCpt(Arrays.asList("Var0"), Arrays.asList("Var1"));
		f2 = builder.addCptFactorWithNewCpt(Arrays.asList("Var1"), Arrays.asList("Var2"));

		allVarNames = Arrays.asList(new String[] {"Var0", "Var1", "Var2"});
		List<String> observedVarNames = Arrays.asList(new String[] {"Var0", "Var2"});

		bn = builder.build();		
		trainingData = new ArrayList<Assignment>();
		a1 = bn.outcomeToAssignment(observedVarNames,
				Arrays.asList(new String[] {"F", "T"}));
		a2 = bn.outcomeToAssignment(allVarNames,
				Arrays.asList(new String[] {"T", "F", "F"}));
		a3 = bn.outcomeToAssignment(observedVarNames,
				Arrays.asList(new String[] {"F", "F"}));
    a4 = bn.outcomeToAssignment(allVarNames,
        Arrays.asList(new String[] {"T", "T", "T"}));
		for (int i = 0; i < 3; i++) {
			trainingData.add(a1);
			trainingData.add(a2);
			trainingData.add(a3);
			trainingData.add(a4);
		}
		
		t = new IncrementalEMTrainer(20, new JunctionTree());
		s = new StepwiseEMTrainer(20, 4, 0.9, JunctionTree.getSupplier(), 3, null);
		
		testAssignment1 = bn.outcomeToAssignment(allVarNames, Arrays.asList("T", "F", "F"));
		testAssignment2 = bn.outcomeToAssignment(allVarNames, Arrays.asList("F", "T", "T"));
	}

	public void testIncrementalEM() {
	  bn.getCurrentParameters().increment(0.1);
		t.train(bn, trainingData);

		System.out.println(f0);
    System.out.println(f1);
		System.out.println(f2);
		
		assertEquals(1.0, f2.getUnnormalizedProbability(testAssignment1), 0.1);
		assertEquals(1.0, f2.getUnnormalizedProbability(testAssignment2), 0.05);
		fail();
	}

	public void testStepwiseEM() {
	  bn.getCurrentParameters().increment(0.1);
		s.train(bn, trainingData);
		
		System.out.println(f0);
		System.out.println(f1);
		System.out.println(f2);
		
		// Stepwise EM loses the smoothing factor, hence the different expected value for this test.
		assertEquals(1.0, f2.getUnnormalizedProbability(testAssignment1), 0.05);
		assertEquals(1.0, f2.getUnnormalizedProbability(testAssignment2), 0.05);
		fail();
	}
	
	public void testRetainSparsity() {
	  fail();
	}
}
