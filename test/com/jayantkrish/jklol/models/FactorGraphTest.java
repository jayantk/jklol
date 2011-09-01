package com.jayantkrish.jklol.models;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.FactorGraph;

public class FactorGraphTest extends TestCase {

	private FactorGraph f;
	private TableFactorBuilder builder;

	public void setUp() {
		f = new FactorGraph();

		DiscreteVariable tfVar = new DiscreteVariable("Three values",
				Arrays.asList(new String[] {"T", "F", "U"}));

		DiscreteVariable otherVar = new DiscreteVariable("Two values",
				Arrays.asList(new String[] {"foo", "bar"}));

		f = f.addVariable("Var0", tfVar);
		f = f.addVariable("Var1", otherVar);
		f = f.addVariable("Var2", tfVar);
		f = f.addVariable("Var3", tfVar);

		builder = new TableFactorBuilder(f.lookupVariables(Arrays.asList(new String[] {"Var0", "Var2", "Var3"})));
		f = f.addFactor(builder.build());

		builder = new TableFactorBuilder(f.lookupVariables(Arrays.asList(new String[] {"Var2", "Var1"})));
		f = f.addFactor(builder.build());
	}

	public void testGetFactorsWithVariable() {
		assertEquals(2,
				f.getFactorsWithVariable(f.getVariableIndex("Var2")).size());
		assertEquals(1,
				f.getFactorsWithVariable(f.getVariableIndex("Var3")).size());
	}

	public void testGetSharedVariables() {
		List<Integer> shared = new ArrayList<Integer>(f.getSharedVariables(0, 1));
		assertEquals(1, shared.size());
		assertEquals(2, (int) shared.get(0));
	}
}
