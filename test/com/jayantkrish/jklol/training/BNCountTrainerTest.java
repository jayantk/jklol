package com.jayantkrish.jklol.training;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import junit.framework.TestCase;

import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.bayesnet.BayesNet;
import com.jayantkrish.jklol.models.bayesnet.BayesNetBuilder;
import com.jayantkrish.jklol.models.bayesnet.CptFactor;
import com.jayantkrish.jklol.util.Assignment;

public class BNCountTrainerTest extends TestCase {

	BayesNet bn;
	BNCountTrainer t;
	List<Assignment> trainingData;

	public void setUp() {
		BayesNetBuilder builder = new BayesNetBuilder();

		DiscreteVariable tfVar = new DiscreteVariable("TrueFalse",
				Arrays.asList(new String[] {"T", "F"}));

		builder.addDiscreteVariable("Var0", tfVar);
		builder.addDiscreteVariable("Var1", tfVar);
		builder.addDiscreteVariable("Var2", tfVar);

		List<String> emptyStringList = Collections.emptyList();
		builder.addCptFactorWithNewCpt(emptyStringList, Arrays.asList(new String[] {"Var0"}));
		builder.addCptFactorWithNewCpt(emptyStringList, Arrays.asList(new String[] {"Var1"}));
		builder.addCptFactorWithNewCpt(Arrays.asList(new String[] {"Var0", "Var1"}), 
				Arrays.asList(new String[] {"Var2"}));

		List<String> allVarNames = Arrays.asList(new String[] {"Var0", "Var1", "Var2"});
		bn = builder.build();

		trainingData = new ArrayList<Assignment>();
		Assignment a1 = bn.outcomeToAssignment(allVarNames,
				Arrays.asList(new String[] {"F", "T", "T"}));
		Assignment a2 = bn.outcomeToAssignment(allVarNames,
				Arrays.asList(new String[] {"T", "T", "F"}));
		Assignment a3 = bn.outcomeToAssignment(allVarNames,
				Arrays.asList(new String[] {"F", "F", "F"}));
		for (int i = 0; i < 3; i++) {
			trainingData.add(a1);
			trainingData.add(a2);
			trainingData.add(a3);
		}
		t = new BNCountTrainer();
	}

	public void testTrain() {
		t.train(bn, trainingData);
		bn.getCurrentParameters().increment(1.0);

		List<CptFactor> cptFactors = bn.getCptFactors();
		CptFactor m0 = cptFactors.get(0);
		assertEquals(4.0 / 11.0,
				m0.getUnnormalizedProbability(m0.getVars().outcomeToAssignment(Arrays.asList(new String[] {"T"}))));

		assertEquals(7.0 / 11.0,
				m0.getUnnormalizedProbability(m0.getVars().outcomeToAssignment(Arrays.asList(new String[] {"F"}))));

		CptFactor m1 = cptFactors.get(1);
		assertEquals(7.0 / 11.0,
				m1.getUnnormalizedProbability(m1.getVars().outcomeToAssignment(Arrays.asList(new String[] {"T"}))));

		assertEquals(4.0 / 11.0,
				m1.getUnnormalizedProbability(m1.getVars().outcomeToAssignment(Arrays.asList(new String[] {"F"}))));

		CptFactor m2 = cptFactors.get(2);

		assertEquals(0.8,
				m2.getUnnormalizedProbability(m2.getVars().outcomeToAssignment(Arrays.asList(new String[] {"T", "T", "F"}))));
		assertEquals(0.2,
				m2.getUnnormalizedProbability(m2.getVars().outcomeToAssignment(Arrays.asList(new String[] {"T", "T", "T"}))));
		assertEquals(0.8,
				m2.getUnnormalizedProbability(m2.getVars().outcomeToAssignment(Arrays.asList(new String[] {"F", "F", "F"}))));

		// Only smoothing applies to this conditional probability
		assertEquals(0.5,
				m2.getUnnormalizedProbability(m2.getVars().outcomeToAssignment(Arrays.asList(new String[] {"T", "F", "T"}))));
	}
}
