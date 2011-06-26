import com.jayantkrish.jklol.models.*;

import junit.framework.*;
import java.util.*;

/**
 * A test of SparseCpts and their interactions with CptTableFactors.
 */
public class SparseCptTest extends TestCase {

    private Cpt cpt;
    private SparseCpt sparse;
    private CptTableFactor f;
    private Variable<String> v;

    private Object[][] assignments;

    public void setUp() {
	v = new Variable<String>("Two values",
		Arrays.asList(new String[] {"T", "F"}));

	f = new CptTableFactor(
		new VariableNumMap(Arrays.asList(new Integer[] {0, 1}), Arrays.asList(new Variable<?>[] {v, v})),
		new VariableNumMap(Arrays.asList(new Integer[] {2, 3}), Arrays.asList(new Variable<?>[] {v, v})));
	
	sparse = new SparseCpt(Arrays.asList(new Variable[] {v, v}), Arrays.asList(new Variable[] {v, v}));
	
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
}