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

	private DiscreteVariable v;
	private DiscreteVariable v2;

	private FeatureFunction feature;

	public void setUp() {
		v = new DiscreteVariable("Three values",
				Arrays.asList(new String[] {"T", "F", "U"}));
		v2 = new DiscreteVariable("Two values",
				Arrays.asList(new String[] {"foo", "bar"}));

		h = new TableFactor(new VariableNumMap(Arrays.asList(new Integer[] {1, 0}),
				Arrays.asList(new DiscreteVariable[] {v2, v})));
		
		f = new TableFactor(new VariableNumMap(Arrays.asList(new Integer[] {0, 3, 2, 5}),
				Arrays.asList(new DiscreteVariable[] {v, v, v, v})));
		// NOTE: These insertions are to the variables in SORTED ORDER,
		// even though the above variables are defined out-of-order.
		f.setWeightList(Arrays.asList(new String[] {"T", "T", "T", "T"}), 1.0);
		f.setWeightList(Arrays.asList(new String[] {"T", "T", "F", "T"}), 3.0);
		f.setWeightList(Arrays.asList(new String[] {"T", "T", "F", "U"}), 2.0);

		g = new TableFactor(new VariableNumMap(Arrays.asList(new Integer[] {0, 1, 3}),
				Arrays.asList(new DiscreteVariable[] {v, v, v})));
		g.setWeightList(Arrays.asList(new String[] {"T", "U", "F"}), 7.0);
		g.setWeightList(Arrays.asList(new String[] {"T", "F", "F"}), 11.0);
		g.setWeightList(Arrays.asList(new String[] {"F", "T", "T"}), 9.0);
		g.setWeightList(Arrays.asList(new String[] {"T", "U", "T"}), 13.0);

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
			f.setWeightList(Arrays.asList(new String[] {"T", "T", "T"}), 3.0);
		} catch (IllegalArgumentException e) {
			return;
		}
		fail("Expected IllegalArgumentException");
	}

	public void testSetProbabilityError2() {
		try {
			f.setWeightList(Arrays.asList(new String[] {"T", "T", "T", "T", "T"}), 3.0);
		} catch (IllegalArgumentException e) {
			return;
		}
		fail("Expected IllegalArgumentException");
	}

	public void testSetProbabilityError3() {
		try {
			f.setWeightList(Arrays.asList(new String[] {"T", "T", "T", "T"}), -1.0);	
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
		TableFactor t = TableFactor.productFactor(Arrays.asList(new DiscreteFactor[] {f, g}));
		assertEquals(14.0,
				t.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "U", "T", "F", "U"})));
		assertEquals(0.0,
				t.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "U", "T", "F", "F"})));       
	}

	public void testProductEmptyFactor() {
		DiscreteFactor m = f.marginalize(Arrays.asList(new Integer[] {0, 3, 2, 5}));
		TableFactor t = TableFactor.productFactor(Arrays.asList(new DiscreteFactor[] {m, f}));
		assertEquals(18.0,
				t.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "T", "F", "T"})));
	}
	
	public void testSumFactor() {
		DiscreteFactor s = TableFactor.sumFactor(f, g);
		assertEquals(3.0 + 7.0,
				s.getUnnormalizedProbability("T", "U", "T", "F", "T"));
		assertEquals(3.0,
				s.getUnnormalizedProbability("T", "T", "T", "F", "T"));
		assertEquals(2.0 + 7.0,
				s.getUnnormalizedProbability("T", "U", "T", "F", "U"));
		
		f = new TableFactor(new VariableNumMap(Arrays.asList(new Integer[] {0, 2, 3, 5}),
				Arrays.asList(new DiscreteVariable[] {v, v, v, v})));
		// NOTE: These insertions are to the variables in SORTED ORDER,
		// even though the above variables are defined out-of-order.
		f.setWeightList(Arrays.asList(new String[] {"T", "T", "T", "T"}), 1.0);
		f.setWeightList(Arrays.asList(new String[] {"T", "T", "F", "T"}), 3.0);
		f.setWeightList(Arrays.asList(new String[] {"T", "T", "F", "U"}), 2.0);

		g = new TableFactor(new VariableNumMap(Arrays.asList(new Integer[] {0, 1, 3}),
				Arrays.asList(new DiscreteVariable[] {v, v, v})));
		g.setWeightList(Arrays.asList(new String[] {"T", "U", "F"}), 7.0);
		g.setWeightList(Arrays.asList(new String[] {"T", "F", "F"}), 11.0);
		g.setWeightList(Arrays.asList(new String[] {"F", "T", "T"}), 9.0);
		g.setWeightList(Arrays.asList(new String[] {"T", "U", "T"}), 13.0);

	}

	public void testComputeExpectation() {
		assertEquals(0.5,
				f.computeExpectation(feature));
	}

	public void testMostLikelyAssignments() {
		List<Assignment> likely = g.mostLikelyAssignments(2);

		assertEquals(2, likely.size());
		assertEquals(13.0, 
				g.getUnnormalizedProbability(likely.get(0)));
		assertEquals(11.0, 
				g.getUnnormalizedProbability(likely.get(1)));
	}
}