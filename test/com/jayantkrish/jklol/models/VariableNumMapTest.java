package com.jayantkrish.jklol.models;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import com.google.common.collect.HashBiMap;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Tests for VariableNumMap.
 * @author jayant
 *
 */
public class VariableNumMapTest extends TestCase {

	private VariableNumMap a,b,c,d;
	private DiscreteVariable v1,v2;

	public void setUp() {
		v1 = new DiscreteVariable("Two values",
				Arrays.asList(new String[] {"T", "F"}));
		v2 = new DiscreteVariable("Three values",
				Arrays.asList(new String[] {"T", "F", "U"}));

		a = new VariableNumMap(Arrays.asList(new Integer[] {0, 1, 3}), 
		    Arrays.asList("v0", "v1", "v3"),
				Arrays.asList(new DiscreteVariable[] {v1, v2, v1}));
		b = new VariableNumMap(Arrays.asList(new Integer[] {2, 1}),
		    Arrays.asList("v2", "v1"),
				Arrays.asList(new DiscreteVariable[] {v1, v2}));
		// c and d are both slightly inconsistent with a and b.
		c = new VariableNumMap(Arrays.asList(new Integer[] {2, 1}),
		    Arrays.asList("v2", "v1"),
				Arrays.asList(new DiscreteVariable[] {v1, v1}));
		d = new VariableNumMap(Arrays.asList(new Integer[] {2, 1}),
		    Arrays.asList("v2", "v7"),
				Arrays.asList(new DiscreteVariable[] {v1, v2}));
	}

	public void testImmutability() {
		List<Integer> inds = new ArrayList<Integer>(Ints.asList(1, 2, 3));
		List<String> names = new ArrayList<String>(Arrays.asList("v1", "v2", "v3"));
		List<DiscreteVariable> vars= new ArrayList<DiscreteVariable>(Arrays.asList(v1,v2,v2));
		VariableNumMap c = new VariableNumMap(inds, names, vars);
		inds.add(4);
		names.add("v4");
		vars.add(v1);
		assertFalse(c.contains(4));
		assertFalse(c.contains("v4"));
		assertEquals(3, c.getVariableNums().size());
		assertEquals(3, c.getVariables().size());
	}
	
	public void testEquals() {
	  assertTrue(b.equals(b));
	  assertFalse(c.equals(b));
	  assertFalse(d.equals(b));
	}
	
	public void testUniqueNames() {
		try {
		  new VariableNumMap(Arrays.asList(new Integer[] {0, 1, 3}), 
		    Arrays.asList("v0", "v1", "v0"),
				Arrays.asList(new DiscreteVariable[] {v1, v2, v1}));
		} catch (IllegalArgumentException e) {
			return;
		}
		fail("Expected IllegalArgumentException");
	}

	public void testGetVariableNums() {
		assertEquals(Arrays.asList(new Integer[] {0, 1, 3}),
				a.getVariableNums());
		// Ensure that the returned values come in sorted order.
		assertEquals(Arrays.asList(new Integer[] {1, 2}),
				b.getVariableNums());
	}

	public void testGetVariables() {
		assertEquals(Arrays.asList(new DiscreteVariable[] {v1,v2,v1}),
				a.getVariables());
		// Ensure that the returned values come in sorted order.
		assertEquals(Arrays.asList(new DiscreteVariable[] {v2,v1}),
				b.getVariables());
		
		assertEquals(0, a.getVariableByName("v0"));
		assertEquals(2, b.getVariableByName("v2"));
	}
	
	public void testGetVariablesByName() {
	  VariableNumMap result = a.getVariablesByName(Arrays.asList("v0", "v3", "v4"));
	  assertEquals(2, result.size());
	  assertEquals(0, result.getVariableByName("v0"));
	  assertEquals(3, result.getVariableByName("v3"));
	}

	public void testIntersection() {
		VariableNumMap intersection = a.intersection(b);

		assertEquals(1, intersection.size());
		assertTrue(intersection.contains(1));
		assertEquals(v2, intersection.getVariable(1));
		assertEquals(1, intersection.getVariableByName("v1"));
	}

	public void testIntersectionError() {
		try {
			a.intersection(c);
		} catch (IllegalArgumentException e) {
			return;
		}
		fail("Expected IllegalArgumentException");
	}

	public void testRemoveAll() {
		VariableNumMap result = a.removeAll(b);
		assertEquals(Arrays.asList(new Integer[] {0,3}), result.getVariableNums());
		assertEquals(Arrays.asList(new DiscreteVariable[] {v1,v1}), result.getVariables());
		assertEquals(Arrays.asList("v0","v3"), result.getVariableNames());
	}

	public void testRemoveAllError() {
		try {
			a.removeAll(c);
		} catch (IllegalArgumentException e) {
			return;
		}
		fail("Expected IllegalArgumentException");
	}

	public void testUnion() {
		VariableNumMap result = a.union(b);
		assertEquals(Arrays.asList(new Integer[] {0,1,2,3}), result.getVariableNums());
		assertEquals(Arrays.asList(new DiscreteVariable[] {v1,v2,v1,v1}), result.getVariables());
		assertEquals(Arrays.asList(new String[] {"v0","v1","v2","v3"}), result.getVariableNames());
	}

	public void testUnionError() {
		try {
			a.union(c);
		} catch (IllegalArgumentException e) {
			return;
		}
		fail("Expected IllegalArgumentException");
	}
	
	public void testRelabelVariables() {
	  HashBiMap<Integer, Integer> indexReplacements = HashBiMap.create();
	  indexReplacements.put(0, 1);
	  indexReplacements.put(1, 2);
	  indexReplacements.put(2, 3);
	  indexReplacements.put(3, 4);
	  HashBiMap<String, String> nameReplacements = HashBiMap.create();
	  nameReplacements.put("v0", "v1");
	  nameReplacements.put("v1", "v2");
	  nameReplacements.put("v2", "v3");
	  nameReplacements.put("v3", "v4");
	  
	  VariableRelabeling relabeling = new VariableRelabeling(indexReplacements, nameReplacements);
	  assertFalse(relabeling.isInDomain(d));
	  assertTrue(relabeling.isInDomain(a));
	  assertFalse(relabeling.isInRange(d));
	  assertFalse(relabeling.isInRange(a));
	  assertTrue(relabeling.isInRange(b));
	  
	  VariableNumMap result = relabeling.apply(a);
	  assertEquals(Arrays.asList("v1", "v2", "v4"), result.getVariableNames());
	  assertEquals(Ints.asList(1, 2, 4), result.getVariableNums());
	  assertEquals(Arrays.asList(v1, v2, v1), result.getVariables());
	  	  
	  result = relabeling.invert(b);
	  b = new VariableNumMap(Arrays.asList(new Integer[] {2, 1}),
	      Arrays.asList("v2", "v1"),
	      Arrays.asList(new DiscreteVariable[] {v1, v2}));

	  assertEquals(Arrays.asList("v0", "v1"), result.getVariableNames());
	  assertEquals(Ints.asList(0, 1), result.getVariableNums());
	  assertEquals(Arrays.asList(v2, v1), result.getVariables());
	}
	
	public void testAssignmentToIntArray() {
	  int[] actual = a.assignmentToIntArray(new Assignment(Arrays.asList(0, 1, 3), 
	      Arrays.<Object>asList("T", "U", "F")));
	  assertTrue(Arrays.equals(actual, new int[] {0, 2, 1}));
	}
	
	public void testIntArrayToAssignment() {
	  Assignment expected = new Assignment(Arrays.asList(0, 1, 3), 
	      Arrays.<Object>asList("T", "U", "F"));
	  Assignment actual = a.intArrayToAssignment(new int[] {0, 2, 1});
	  assertEquals(expected, actual);
	}
}
