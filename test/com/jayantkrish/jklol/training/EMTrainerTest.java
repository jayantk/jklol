package com.jayantkrish.jklol.training;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.bayesnet.BayesNet;
import com.jayantkrish.jklol.models.bayesnet.BayesNetBuilder;
import com.jayantkrish.jklol.models.bayesnet.Cpt;
import com.jayantkrish.jklol.models.bayesnet.CptFactor;
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

	Assignment a1,a2,a3;
	List<String> allVarNames;

	StepwiseEMTrainer s;

	String[][] testAssignments = new String[][] {{"F", "F", "T"}, {"T", "F", "F"}, 
			{"T", "T", "T"}};
	double[] incrementalEmExpected = new double[] {(7.0 / 11.0) * (7.0 / 11.0)};
	double[] stepwiseEmExpected = new double[] {};
	double TOLERANCE = 0.05;

	public void setUp() {
		BayesNetBuilder builder = new BayesNetBuilder();

		DiscreteVariable tfVar = new DiscreteVariable("TrueFalse",
				Arrays.asList(new String[] {"T", "F"}));

		builder.addDiscreteVariable("Var0", tfVar);
		builder.addDiscreteVariable("Var1", tfVar);
		builder.addDiscreteVariable("Var2", tfVar);

		CptTableFactor f0 = builder.addCptFactor(Collections.<String>emptyList(), Arrays.asList(new String[] {"Var0"}));
		CptTableFactor f1 = builder.addCptFactor(Collections.<String>emptyList(), Arrays.asList(new String[] {"Var1"}));
		CptTableFactor f2 = builder.addCptFactor(Arrays.asList(new String[] {"Var0", "Var1"}), 
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

		t = new IncrementalEMTrainer(10, 1.0, new JunctionTree());
		s = new StepwiseEMTrainer(10, 3, 1.0, 0.9, new JunctionTree());
	}

	public void testIncrementalEM() {
		t.train(bn, trainingData);

		//Assignment a = bn.outcomeToAssignment(allVarNames, 
		//	Arrays.asList());

		for (CptFactor f : bn.getCptFactors()) {
			System.out.println(f);
		}
	}

	public void testStepwiseEM() {
		s.train(bn, trainingData);

		for (CptFactor f : bn.getCptFactors()) {
			System.out.println(f);
		}
	}
}
