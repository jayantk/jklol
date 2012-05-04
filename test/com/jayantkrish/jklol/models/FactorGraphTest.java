package com.jayantkrish.jklol.models;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.FactorGraphProtos.FactorGraphProto;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.util.Assignment;

public class FactorGraphTest extends TestCase {

	private FactorGraph f;
	private TableFactorBuilder builder;
	private DiscreteVariable tfVar;

	public void setUp() {
		f = new FactorGraph();

		tfVar = new DiscreteVariable("Three values",
				Arrays.asList(new String[] {"T", "F", "U"}));

		DiscreteVariable otherVar = new DiscreteVariable("Two values",
				Arrays.asList(new String[] {"foo", "bar"}));

		f = f.addVariable("Var0", tfVar);
		f = f.addVariable("Var1", otherVar);
		f = f.addVariable("Var2", tfVar);
		f = f.addVariable("Var3", tfVar);

		builder = new TableFactorBuilder(f.getVariables().getVariablesByName(Arrays.asList("Var0", "Var2", "Var3")),
		    SparseTensorBuilder.getFactory());
		builder.incrementWeight(builder.getVars().intArrayToAssignment(new int[] {0, 0, 0}), 1.0);
		f = f.addFactor("f1", builder.build());

		builder = new TableFactorBuilder(f.getVariables().getVariablesByName(Arrays.asList("Var2", "Var1")),
		    SparseTensorBuilder.getFactory());
		builder.incrementWeight(builder.getVars().intArrayToAssignment(new int[] {0, 0}), 1.0);
		builder.incrementWeight(builder.getVars().intArrayToAssignment(new int[] {1, 1}), 1.0);
		f = f.addFactor("f2", builder.build());
	}

	public void testGetFactorsWithVariable() {
		assertEquals(2,
				f.getFactorsWithVariable(f.getVariables().getVariableByName("Var2")).size());
		assertEquals(1,
				f.getFactorsWithVariable(f.getVariables().getVariableByName("Var3")).size());
	}

	public void testGetSharedVariables() {
		List<Integer> shared = new ArrayList<Integer>(f.getSharedVariables(0, 1));
		assertEquals(1, shared.size());
		assertEquals(2, (int) shared.get(0));
	}
	
	public void testAddVariable() {
	  f = f.addVariable("Var4", tfVar);
	  assertEquals(5, f.getVariables().size());
	  
	  try {
	    f.addVariable("Var4", tfVar);
	  } catch (IllegalArgumentException e) {
	    return;
	  }
	  fail("Expected IllegalArgumentException");
	}
	
	public void testMarginalize() {
	  FactorGraph m = f.marginalize(Ints.asList(0, 3, 2));
	  assertEquals(1, m.getVariables().size());
	  assertTrue(m.getVariables().getVariableNames().contains("Var1"));
	  
	  assertEquals(1, m.getFactors().size());
	  Factor factor = m.getFactors().get(0);
	  assertEquals(1, factor.getVars().size());
	  assertEquals(1.0, factor.getUnnormalizedProbability("foo"));
	  assertEquals(0.0, factor.getUnnormalizedProbability("bar"));
	}
	
	public void testConditional1() {
	  Assignment a = f.outcomeToAssignment(Arrays.asList("Var0"), 
	      Arrays.asList("T"));
	  Assignment b = f.outcomeToAssignment(Arrays.asList("Var1"), 
	      Arrays.asList("foo"));

	  FactorGraph c = f.conditional(a).conditional(b);
	  assertEquals(2, c.getVariables().size());
	  assertTrue(c.getVariables().getVariableNames().contains("Var2")); 
	  assertFalse(c.getVariables().getVariableNames().contains("Var1"));
	  assertEquals(a.union(b), c.getConditionedValues());
	  
	  Assignment a2 = f.outcomeToAssignment(Arrays.asList("Var2", "Var3"), Arrays.asList("T", "T")); 
	  assertEquals(1.0, c.getUnnormalizedProbability(a2));
	  a2 = f.outcomeToAssignment(Arrays.asList("Var2", "Var3"), Arrays.asList("F", "T")); 
	  assertEquals(0.0, c.getUnnormalizedProbability(a2));
	}
	
	public void testConditional2() {
	  Assignment a = f.outcomeToAssignment(Arrays.asList("Var0", "Var1"), 
	      Arrays.asList("T", "bar"));
	  FactorGraph c = f.conditional(a);

	  Assignment a2 = f.outcomeToAssignment(Arrays.asList("Var2", "Var3"), Arrays.asList("T", "T")); 
	  assertEquals(0.0, c.getUnnormalizedProbability(a2));
	  a2 = f.outcomeToAssignment(Arrays.asList("Var2", "Var3"), Arrays.asList("F", "T")); 
	  assertEquals(0.0, c.getUnnormalizedProbability(a2));
	}
	
	public void testConditional3() {
	  Assignment a = f.outcomeToAssignment(
	      Arrays.asList("Var0", "Var1", "Var2", "Var3"),
	      Arrays.asList("T", "foo", "T", "T"));
	  FactorGraph c = f.conditional(a);

	  assertEquals(0, c.getVariables().size());
	  assertEquals(1.0, c.getUnnormalizedProbability(Assignment.EMPTY));
	}
	
	public void testSerialization() {
	  FactorGraphProto proto = f.toProto();
	  FactorGraph copy = FactorGraph.fromProto(proto);
	  
	  assertEquals(f.getVariables(), copy.getVariables());
	  
	  Factor copy1 = copy.getFactor(0);
	  assertEquals(1.0, copy1.getUnnormalizedProbability("T", "T", "T"));
	  assertTrue(copy1 instanceof TableFactor);
	  assertEquals(1.0, copy1.size());
	  
	  Factor copy2 = copy.getFactor(1);
	  assertEquals(1.0, copy2.getUnnormalizedProbability("foo", "T"));
	  assertEquals(1.0, copy2.getUnnormalizedProbability("bar", "F"));
	  assertEquals(2.0, copy2.size());
	}
}
