package com.jayantkrish.jklol.models;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.dynamic.Plate;
import com.jayantkrish.jklol.models.dynamic.VariablePattern;
import com.jayantkrish.jklol.util.Assignment;

public class FactorGraphTest extends TestCase {

	private FactorGraph f, dynamic;
	private TableFactorBuilder builder;
	private DiscreteVariable tfVar;
	private VariablePattern dynamicPattern; 

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

		builder = new TableFactorBuilder(f.getVariables().getVariablesByName(Arrays.asList("Var0", "Var2", "Var3")));
		builder.incrementWeight(builder.getVars().intArrayToAssignment(new int[] {0, 0, 0}), 1.0);
		f = f.addFactor(builder.build());

		builder = new TableFactorBuilder(f.getVariables().getVariablesByName(Arrays.asList("Var2", "Var1")));
		builder.incrementWeight(builder.getVars().intArrayToAssignment(new int[] {0, 0}), 1.0);
		builder.incrementWeight(builder.getVars().intArrayToAssignment(new int[] {1, 1}), 1.0);
		f = f.addFactor(builder.build());
		
		// Construct a dynamic factor graph.
		ObjectVariable plateVar = new ObjectVariable(List.class);
		dynamic = f.addVariable("PlateReplications", plateVar);
		VariableNumMap templateVariables = new VariableNumMap(Ints.asList(0, 1),
		    Arrays.asList("x", "y"), Arrays.<Variable>asList(tfVar, otherVar));
		dynamicPattern = VariablePattern.fromTemplateVariables(templateVariables, VariableNumMap.emptyMap());
		Plate plate = new Plate(dynamic.getVariables().getVariablesByName("PlateReplications"), dynamicPattern);
		
		dynamic = dynamic.addPlate(plate); 
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
	  assertTrue(m.getVariableNames().contains("Var1"));
	  
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
	  assertTrue(c.getVariableNames().contains("Var2")); 
	  assertFalse(c.getVariableNames().contains("Var1"));
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
	
	public void testConditionalDynamic1() {
	  List<Assignment> assignments = Lists.newArrayList();
	  for (int i = 0; i < 5; i++) {
	    assignments.add(Assignment.EMPTY);
	  }
	  
	  Assignment a = dynamic.outcomeToAssignment(
	      Arrays.asList("Var0", "PlateReplications"), Arrays.<Object>asList("T", assignments));
	  
	  FactorGraph c = dynamic.conditional(a);
	  assertEquals(13, c.getVariables().size());
	}
	
	public void testConditionalDynamic2() {
	  List<Assignment> assignments = Lists.newArrayList();
	  assignments.add(dynamicPattern.getTemplateVariables().getVariablesByName("x")
	      .outcomeArrayToAssignment("T"));
	  assignments.add(Assignment.EMPTY);
	  assignments.add(dynamicPattern.getTemplateVariables().getVariablesByName("y")
	      .outcomeArrayToAssignment("foo"));

	  Assignment a = dynamic.outcomeToAssignment(
	      Arrays.asList("Var0", "PlateReplications"), Arrays.<Object>asList("T", assignments));

	  FactorGraph c = dynamic.conditional(a);
	  assertEquals(7, c.getVariables().size());
	  assertEquals(1, c.getConditionedVariables().getVariablesByName("x-0").size());
	  assertEquals(1, c.getVariables().getVariablesByName("y-0").size());
	}
}
