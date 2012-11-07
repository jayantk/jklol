package com.jayantkrish.jklol.training;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.bayesnet.CptTableFactor;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraphBuilder;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

public class BNCountTrainerTest extends TestCase {

	ParametricFactorGraph bn;
	BNCountTrainer t;
	List<DynamicAssignment> trainingData;

	public void setUp() {
		ParametricFactorGraphBuilder builder = new ParametricFactorGraphBuilder();

		DiscreteVariable tfVar = new DiscreteVariable("TrueFalse",
				Arrays.asList(new String[] {"T", "F"}));

		builder.addVariable("Var0", tfVar);
		builder.addVariable("Var1", tfVar);
		builder.addVariable("Var2", tfVar);

		VariableNumMap var0 = builder.getVariables().getVariablesByName("Var0");
		VariableNumMap var1 = builder.getVariables().getVariablesByName("Var1");
		VariableNumMap var2 = builder.getVariables().getVariablesByName("Var2");
		
		builder.addUnreplicatedFactor("root-0", new CptTableFactor(VariableNumMap.emptyMap(), var0));
		builder.addUnreplicatedFactor("root-1", new CptTableFactor(VariableNumMap.emptyMap(), var1));
		builder.addUnreplicatedFactor("01->2", new CptTableFactor(var1.union(var0), var2));

		bn = builder.build();

		trainingData = new ArrayList<DynamicAssignment>();
		DynamicAssignment a1 = bn.getVariables().outcomeToAssignment("F", "T", "T");
		DynamicAssignment a2 = bn.getVariables().outcomeToAssignment("T", "T", "F");
		DynamicAssignment a3 = bn.getVariables().outcomeToAssignment("F", "F", "F");
		for (int i = 0; i < 3; i++) {
			trainingData.add(a1);
			trainingData.add(a2);
			trainingData.add(a3);
		}
		t = new BNCountTrainer();
	}

	public void testTrain() {
		SufficientStatistics parameters = t.train(bn, trainingData);
		parameters.increment(1.0);
		
		FactorGraph factorGraph = bn.getModelFromParameters(parameters).getFactorGraph(DynamicAssignment.EMPTY);
		
		// TODO(jayantk): This test depends on the bayes net preserving the order
		// of the factors when constructing the factor graph.
		List<Factor> factors = factorGraph.getFactors();
		Factor m0 = factors.get(0);
		assertEquals(4.0 / 11.0,
				m0.getUnnormalizedProbability(m0.getVars().outcomeToAssignment(Arrays.asList(new String[] {"T"}))));
		assertEquals(7.0 / 11.0,
				m0.getUnnormalizedProbability(m0.getVars().outcomeToAssignment(Arrays.asList(new String[] {"F"}))));

		Factor m1 = factors.get(1);
		assertEquals(7.0 / 11.0,
				m1.getUnnormalizedProbability(m1.getVars().outcomeToAssignment(Arrays.asList(new String[] {"T"}))));
		assertEquals(4.0 / 11.0,
				m1.getUnnormalizedProbability(m1.getVars().outcomeToAssignment(Arrays.asList(new String[] {"F"}))));

		Factor m2 = factors.get(2);
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
