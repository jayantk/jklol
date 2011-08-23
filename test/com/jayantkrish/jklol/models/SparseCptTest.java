import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;

import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.bayesnet.CptTableFactor;
import com.jayantkrish.jklol.models.bayesnet.SparseCpt;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A test of SparseCpts and their interactions with CptTableFactors.
 */
public class SparseCptTest extends TestCase {

	private SparseCpt sparse;
	private CptTableFactor f;
	private DiscreteVariable v;

	private Object[][] assignments;

	public void setUp() {
		v = new DiscreteVariable("Two values",
				Arrays.asList(new String[] {"T", "F"}));

		f = new CptTableFactor(
				new VariableNumMap(Arrays.asList(new Integer[] {0, 1}), Arrays.asList(new DiscreteVariable[] {v, v})),
				new VariableNumMap(Arrays.asList(new Integer[] {2, 3}), Arrays.asList(new DiscreteVariable[] {v, v})));

		sparse = new SparseCpt(Arrays.asList(new DiscreteVariable[] {v, v}), Arrays.asList(new DiscreteVariable[] {v, v}));

		Map<Integer, Integer> cptVarNumMap = new HashMap<Integer, Integer>();
		for (int i = 0; i < 4; i++) {
			cptVarNumMap.put(i, i);
		}

		// Note: Parent F, T was unassigned!
		assignments = new Object[][] {{"T", "T", "T", "T"},
				{"T", "T", "F", "T"},
				{"T", "F", "F", "T"},
				{"F", "F", "F", "F"},
				{"F", "F", "T", "T"}};
		for (int i = 0; i < assignments.length; i++) {
			sparse.setNonZeroProbabilityOutcome(f.getVars().outcomeToAssignment(Arrays.asList(assignments[i])));
		}
		f.setCpt(sparse, cptVarNumMap);
	}


	public void testSmoothing() {
		f.addUniformSmoothing(1.0);

		assertEquals(0.5, f.getUnnormalizedProbability(f.getVars().outcomeToAssignment(assignments[0])));
		assertEquals(0.0, f.getUnnormalizedProbability(f.getVars().outcomeToAssignment(new Object[] {"T", "T", "F", "F"})));
		assertEquals(1.0, f.getUnnormalizedProbability(f.getVars().outcomeToAssignment(assignments[2])));
	}

	public void testUnsetParentError() {
		try {
			f.getUnnormalizedProbability(f.getVars().outcomeToAssignment(new Object[] {"F", "T", "T", "T"}));
		} catch (RuntimeException e) {
			return;
		}
		fail("Expected RuntimeException");
	}

    public void testIteration() {
	Iterator<Assignment> iter = f.outcomeIterator();

	Set<Assignment> shouldBeInIter = new HashSet<Assignment>();
	for (int i = 0; i < assignments.length; i++) {
	    shouldBeInIter.add(f.getVars().outcomeToAssignment(assignments[i]));
	}
	
	while (iter.hasNext()) {
	    Assignment a = iter.next();
	    assertTrue(shouldBeInIter.contains(a));
	    // If this outcome isn't possible, this method will throw a runtime exception
	    f.getUnnormalizedProbability(a);
	}
    }
}
