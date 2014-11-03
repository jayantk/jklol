package com.jayantkrish.jklol.models;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.DiscreteFactor.Outcome;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IntBiMap;

/**
 * This also tests many of the methods in Factor and the
 * IndicatorFeatureFunction.
 */ 
public class TableFactorTest extends TestCase {

	private TableFactor f;
	private TableFactor g;
	private TableFactor h;
	
	private TableFactorBuilder builder;

	private DiscreteVariable v;
	private DiscreteVariable v2;

	public void setUp() {
		v = new DiscreteVariable("Three values",
				Arrays.asList(new String[] {"T", "F", "U"}));
		v2 = new DiscreteVariable("Two values",
				Arrays.asList(new String[] {"foo", "bar"}));

		h = new TableFactorBuilder(new VariableNumMap(Arrays.asList(new Integer[] {1, 0}),
		    Arrays.asList("v1", "v0"), Arrays.asList(new DiscreteVariable[] {v2, v})), 
		    SparseTensorBuilder.getFactory()).build();
				
		builder = new TableFactorBuilder(new VariableNumMap(Arrays.asList(new Integer[] {0, 1, 3}),
		    Arrays.asList("v0", "v1", "v3"), Arrays.asList(new DiscreteVariable[] {v, v, v})), 
		    SparseTensorBuilder.getFactory()); 
		builder.setWeightList(Arrays.asList(new String[] {"T", "U", "F"}), 7.0);
		builder.setWeightList(Arrays.asList(new String[] {"T", "F", "F"}), 11.0);
		builder.setWeightList(Arrays.asList(new String[] {"F", "T", "T"}), 9.0);
		builder.setWeightList(Arrays.asList(new String[] {"T", "U", "T"}), 13.0);
		g = builder.build();
		
		builder = new TableFactorBuilder(
		    new VariableNumMap(Arrays.asList(new Integer[] {0, 3, 2, 5}), Arrays.asList("v0", "v3", "v2", "v5"),
				Arrays.asList(new DiscreteVariable[] {v, v, v, v})), SparseTensorBuilder.getFactory());
		// NOTE: These insertions are to the variables in SORTED ORDER,
		// even though the above variables are defined out-of-order.
		builder.setWeightList(Arrays.asList(new String[] {"T", "T", "T", "T"}), 1.0);
		builder.setWeightList(Arrays.asList(new String[] {"T", "T", "F", "T"}), 3.0);
		builder.setWeightList(Arrays.asList(new String[] {"T", "T", "F", "U"}), 2.0);
		f = builder.build();

		Set<Assignment> testAssignments = new HashSet<Assignment>();
		testAssignments.add(f.getVars().outcomeToAssignment(Arrays.asList(new String[] {"T", "T", "T", "T"})));
		testAssignments.add(f.getVars().outcomeToAssignment(Arrays.asList(new String[] {"T", "F", "F", "F"})));
		testAssignments.add(f.getVars().outcomeToAssignment(Arrays.asList(new String[] {"T", "T", "F", "U"})));
	}

	public void testVariableOrder() {
		assertEquals(Arrays.asList(new Integer[] {0, 1}),
				h.getVars().getVariableNums());
		assertEquals(Arrays.asList(new DiscreteVariable[] {v, v2}),
				h.getVars().getVariables());
	}

	public void testGetSetProbability() {
		assertEquals(1.0,
				f.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "T", "T", "T"})));
		assertEquals(0.0,
				f.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "F", "F", "F"})));
	}
	
	public void testGetProbability() {
		Assignment a = Assignment.fromSortedArrays(new int[] {0, 1, 2, 3, 5},
				new String[] {"T", "F", "T", "F", "T"});
		assertEquals(3.0, f.getUnnormalizedProbability(a));
	}

	public void testGetProbabilityError() {
		try {
			f.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "T", "T"}));
		} catch (IllegalArgumentException e) {
			return;
		}
		fail("Expected IllegalArgumentException");
	}


	public void testGetProbabilityError2() {
		try {
			f.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "T", "T", "T", "T", "T"}));
		} catch (IllegalArgumentException e) {
			return;
		}
		fail("Expected IllegalArgumentException");
	}

	public void testSetProbabilityError() {
		try {
			builder.setWeightList(Arrays.asList(new String[] {"T", "T", "T"}), 3.0);
		} catch (IllegalArgumentException e) {
			return;
		}
		fail("Expected IllegalArgumentException");
	}

	public void testSetProbabilityError2() {
		try {
			builder.setWeightList(Arrays.asList(new String[] {"T", "T", "T", "T", "T"}), 3.0);
		} catch (IllegalArgumentException e) {
			return;
		}
		fail("Expected IllegalArgumentException");
	}

	public void testMarginalize() {
		DiscreteFactor m = f.marginalize(Arrays.asList(new Integer[] {5, 2}));

		assertEquals(Arrays.asList(new Integer[] {0, 3}), m.getVars().getVariableNums());
		assertEquals(1.0, 
				m.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "T"})));
		assertEquals(5.0,
				m.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "F"})));
	}

	public void testMarginalizeToNothing() {
		DiscreteFactor m = f.marginalize(Arrays.asList(new Integer[] {0, 3, 2, 5}));

		assertEquals(6.0,
				m.getUnnormalizedProbability(Arrays.asList(new String[] {})));
	}

	public void testMaxMarginalize() {
		DiscreteFactor m = f.maxMarginalize(Arrays.asList(new Integer[] {5, 2}));

		assertEquals(Arrays.asList(new Integer[] {0, 3}), m.getVars().getVariableNums());
		assertEquals(3.0, 
				m.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "F"})));
		assertEquals(1.0,
				m.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "T"})));
	}

	public void testMaxMarginalizeToNothing() {
		DiscreteFactor m = f.maxMarginalize(Arrays.asList(new Integer[] {0, 3, 2, 5}));

		assertEquals(3.0,
				m.getUnnormalizedProbability(Arrays.asList(new String[] {})));
	}

	public void testConditionalNone() {
		Factor c = f.conditional(Assignment.fromSortedArrays(new int[] {6, 8},
				new Object[] {"F", "F"}));
		// Nothing should change.
		assertEquals(1.0,
				c.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "T", "T", "T"})));
		assertEquals(0.0,
				c.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "F", "F", "F"})));
	}

	public void testConditionalAll() {
		Factor c = f.conditional(Assignment.fromSortedArrays(new int[] {0, 2, 3, 5},
				new String[] {"T", "T", "F", "T"}));

		assertEquals(3.0, c.getUnnormalizedProbability(Assignment.EMPTY));
	}

	public void testConditionalPartial() {
		Factor c = f.conditional(Assignment.fromSortedArrays(new int[] {0, 3},
		    new String[] {"T", "F"}));

		assertEquals(3.0,
				c.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "T"})));

		assertEquals(2.0,
				c.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "U"})));

		assertEquals(0.0,
				c.getUnnormalizedProbability(Arrays.asList(new String[] {"F", "F"})));
	}

	public void testProduct() {
		DiscreteFactor t = f.product(g.marginalize(1)).coerceToDiscrete();
		
		assertEquals(36.0,
				t.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "T", "F", "U"})));
		assertEquals(0.0,
				t.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "T", "F", "F"})));       
	}
	
	public void testProductList() {
		DiscreteFactor t = f.product(Arrays.asList(g.marginalize(1), g.marginalize(1))).coerceToDiscrete();
		
		assertEquals(2.0 * 18.0 * 18.0,
				t.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "T", "F", "U"})));
		assertEquals(0.0,
				t.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "T", "F", "F"})));       
	}


	public void testProductEmptyFactor() {
		DiscreteFactor m = f.marginalize(Arrays.asList(new Integer[] {0, 3, 2, 5}));
		DiscreteFactor t = f.product(m).coerceToDiscrete();
		assertEquals(18.0,
				t.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "T", "F", "T"})));
	}
	
	public void testOuterProduct() {
	  DiscreteFactor gMarg = g.marginalize(0, 3).coerceToDiscrete();
	  
	  DiscreteFactor outerProd = gMarg.outerProduct(f);
	  assertEquals(gMarg.getVars().union(f.getVars()), outerProd.getVars());
	  assertEquals(9.0, outerProd.size());
	  assertEquals(20.0, outerProd.getUnnormalizedProbability("T", "U", "T", "T", "T"));
	  assertEquals(11.0, outerProd.getUnnormalizedProbability("T", "F", "T", "T", "T"));
	  assertEquals(60.0, outerProd.getUnnormalizedProbability("T", "U", "T", "F", "T"));
	  assertEquals(33.0, outerProd.getUnnormalizedProbability("T", "F", "T", "F", "T"));
	  assertEquals(0.0, outerProd.getUnnormalizedProbability("T", "F", "T", "F", "F"));
	}
	
	public void testInverse() {
	  DiscreteFactor inverse = f.inverse();
	  
	  assertEquals(1.0, inverse.getUnnormalizedProbability(Arrays.asList("T", "T", "T", "T")));
	  assertEquals(1.0 / 3.0, inverse.getUnnormalizedProbability(Arrays.asList("T", "T", "F", "T")));
	  assertEquals(1.0 / 2.0, inverse.getUnnormalizedProbability(Arrays.asList("T", "T", "F", "U")));
	  assertEquals(0.0, inverse.getUnnormalizedProbability(Arrays.asList("U", "U", "U", "U")));
	}
	
	public void testSumFactor() {
		DiscreteFactor s = f.marginalize(2, 5).add(g.marginalize(1)).coerceToDiscrete();
		assertEquals(1.0 + 13.0,
				s.getUnnormalizedProbability("T", "T"));
		assertEquals(5.0 + 18.0,
            s.getUnnormalizedProbability("T", "F"));
		assertEquals(0.0 + 9.0,
            s.getUnnormalizedProbability("F", "T"));
		assertEquals(0.0 + 0.0,
            s.getUnnormalizedProbability("F", "F"));
	}

	public void testMostLikelyAssignments() {
		List<Assignment> likely = g.getMostLikelyAssignments(2);

		assertEquals(2, likely.size());
		assertEquals(13.0, 
				g.getUnnormalizedProbability(likely.get(0)));
		assertEquals(11.0, 
				g.getUnnormalizedProbability(likely.get(1)));
		
		likely = g.getMostLikelyAssignments(5);
		assertEquals(4, likely.size());
	}
	
	public void testMostLikelyAssignmentsTied() {
	  builder = new TableFactorBuilder(new VariableNumMap(Arrays.asList(new Integer[] {0, 1, 3}),
	      Arrays.asList("v0", "v1", "v3"), Arrays.asList(new DiscreteVariable[] {v, v, v})),
	      SparseTensorBuilder.getFactory()); 
	  builder.setWeightList(Arrays.asList(new String[] {"T", "U", "F"}), 2.0);
	  builder.setWeightList(Arrays.asList(new String[] {"T", "F", "F"}), 2.0);
	  builder.setWeightList(Arrays.asList(new String[] {"F", "T", "T"}), 1.0);
	  TableFactor test = builder.build();

	  Assignment first = test.getMostLikelyAssignments(1).get(0);
	  
	  // This should randomize internally in order for some learning algorithms to work.
	  for (int i = 0; i < 100; i++) {
	    if (!first.equals(test.getMostLikelyAssignments(1).get(0))) {
	      // Success. Some randomization occurred.
	      return;
	    }
	  }
	  fail("getMostLikelyAssignments must randomize between equal probability assignments");
	}
	
	public void testRelabelVariables() {
	  VariableNumMap inputVars = new VariableNumMap(Ints.asList(0, 1, 3),
	      Arrays.asList("v0", "v1", "v3"), Arrays.<Variable>asList(null, null, null));
	  VariableNumMap outputVars = new VariableNumMap(Ints.asList(0, 1, 2),
	      Arrays.asList("v0", "v1", "v2"), Arrays.<Variable>asList(null, null, null));
	  int[] keys = new int[] {0, 3, 1};
	  int[] values = new int[] {2, 1, 0};
	  IntBiMap map = IntBiMap.fromUnsortedKeyValues(keys, values);
	  VariableRelabeling relabeling = new VariableRelabeling(inputVars, outputVars, map);
	  
	  TableFactor r = g.relabelVariables(relabeling);
	  assertEquals(3, r.getVars().size());
	  assertTrue(r.getVars().containsAll(Arrays.asList(0, 1, 2)));
	  assertEquals(7.0, r.getUnnormalizedProbability(r.getVars().outcomeArrayToAssignment("U", "F", "T")));
	  assertEquals(0.0, r.getUnnormalizedProbability(r.getVars().outcomeArrayToAssignment("T", "U", "F")));
	  assertEquals(4.0, r.size());
	}
	
	public void testOutcomePrefixIterator() {
	  VariableNumMap prefixVars = g.getVars().getFirstVariables(2);
	  assertTrue(prefixVars.contains(0) && prefixVars.contains(1) && !prefixVars.contains(3));
	  
	  Assignment prefix = prefixVars.outcomeArrayToAssignment("T", "U");
	  Iterator<Outcome> outcomes = g.outcomePrefixIterator(prefix);
	  assertEquals(g.getVars().outcomeArrayToAssignment("T", "U", "T"), outcomes.next().getAssignment());
	  assertEquals(g.getVars().outcomeArrayToAssignment("T", "U", "F"), outcomes.next().getAssignment());
	  assertFalse(outcomes.hasNext());
	  
	  prefix = prefixVars.outcomeArrayToAssignment("F", "F");
	  outcomes = g.outcomePrefixIterator(prefix);
	  assertFalse(outcomes.hasNext());
	}
	
	public void testIterationEmpty() {
	  TableFactor emptyFactor = TableFactorBuilder.ones(VariableNumMap.EMPTY).build();
	  Iterator<Outcome> iter = emptyFactor.outcomeIterator();
	  assertTrue(iter.hasNext());
	  assertEquals(Assignment.EMPTY, iter.next().getAssignment());
	  assertFalse(iter.hasNext());
	}
	
	public void testFromCsvFile() {
	  VariableNumMap firstVal = h.getVars().getVariablesByName("v1");
	  VariableNumMap secondVal = h.getVars().getVariablesByName("v0");
	  List<String> lines = Arrays.asList(new String[] {"foo,F,2.0", "bar,T,3.0"});
	  TableFactor factor = TableFactor.fromDelimitedFile(Arrays.asList(firstVal, secondVal), 
	      lines, ",", false);
	  
	  assertEquals(2.0, factor.size());
	  assertEquals(2.0, factor.getUnnormalizedProbability("F", "foo"));
	  assertEquals(3.0, factor.getUnnormalizedProbability("T", "bar"));
	}
}