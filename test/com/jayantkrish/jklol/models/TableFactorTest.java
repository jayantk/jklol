package com.jayantkrish.jklol.models;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.loglinear.FeatureFunction;
import com.jayantkrish.jklol.models.loglinear.IndicatorFeatureFunction;
import com.jayantkrish.jklol.util.Assignment;

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

	private FeatureFunction feature;

	public void setUp() {
		v = new DiscreteVariable("Three values",
				Arrays.asList(new String[] {"T", "F", "U"}));
		v2 = new DiscreteVariable("Two values",
				Arrays.asList(new String[] {"foo", "bar"}));

		h = new TableFactorBuilder(new VariableNumMap(Arrays.asList(new Integer[] {1, 0}),
				Arrays.asList(new DiscreteVariable[] {v2, v}))).build();
				
		builder = new TableFactorBuilder(new VariableNumMap(Arrays.asList(new Integer[] {0, 1, 3}),
				Arrays.asList(new DiscreteVariable[] {v, v, v}))); 
		builder.setWeightList(Arrays.asList(new String[] {"T", "U", "F"}), 7.0);
		builder.setWeightList(Arrays.asList(new String[] {"T", "F", "F"}), 11.0);
		builder.setWeightList(Arrays.asList(new String[] {"F", "T", "T"}), 9.0);
		builder.setWeightList(Arrays.asList(new String[] {"T", "U", "T"}), 13.0);
		g = builder.build();
		
		builder = new TableFactorBuilder(
		    new VariableNumMap(Arrays.asList(new Integer[] {0, 3, 2, 5}),
				Arrays.asList(new DiscreteVariable[] {v, v, v, v})));
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
		feature = new IndicatorFeatureFunction(testAssignments);
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
		Assignment a = new Assignment(Arrays.asList(new Integer[] {0, 1, 2, 3, 5}),
				Arrays.asList(new String[] {"T", "F", "T", "F", "T"}));
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

	public void testSetProbabilityError3() {
		try {
			builder.setWeightList(Arrays.asList(new String[] {"T", "T", "T", "T"}), -1.0);	
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
		Factor c = f.conditional(new Assignment(Arrays.asList(new Integer[] {6, 8}),
				Arrays.asList(new Object[] {"F", "F"})));
		// Nothing should change.
		assertEquals(1.0,
				c.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "T", "T", "T"})));
		assertEquals(0.0,
				c.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "F", "F", "F"})));
	}

	public void testConditionalAll() {
		Factor c = f.conditional(new Assignment(Arrays.asList(new Integer[] {0, 2, 3, 5}),
				Arrays.asList(new String[] {"T", "T", "F", "T"})));

		assertEquals(3.0,
				c.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "T", "F", "T"})));

		assertEquals(0.0,
				c.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "T", "T", "T"})));

		assertEquals(0.0,
				c.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "F", "F", "F"})));
	}

	public void testConditionalPartial() {
		Factor c = f.conditional(new Assignment(Arrays.asList(new Integer[] {0, 3}),
				Arrays.asList(new String[] {"T", "F"})));

		assertEquals(3.0,
				c.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "T", "F", "T"})));

		assertEquals(2.0,
				c.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "T", "F", "U"})));

		assertEquals(0.0,
				c.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "T", "T", "T"})));

		assertEquals(0.0,
				c.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "F", "F", "F"})));
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

	public void testComputeExpectation() {
		assertEquals(3.0,
				f.computeExpectation(feature));
	}

	public void testMostLikelyAssignments() {
		List<Assignment> likely = g.getMostLikelyAssignments(2);

		assertEquals(2, likely.size());
		assertEquals(13.0, 
				g.getUnnormalizedProbability(likely.get(0)));
		assertEquals(11.0, 
				g.getUnnormalizedProbability(likely.get(1)));
		
		likely = g.getMostLikelyAssignments(5);
		assertEquals(5, likely.size());
		assertEquals(0.0, g.getUnnormalizedProbability(likely.get(4)));
	}
}