import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;

/**
 * Tests for VariableNumMap.
 * @author jayant
 *
 */
public class VariableNumMapTest extends TestCase {

	private VariableNumMap a,b,c;
	private Variable v1,v2;
	
	public void setUp() {
		v1 = new Variable<String>("Two values",
				Arrays.asList(new String[] {"T", "F"}));
		v2 = new Variable<String>("Three values",
				Arrays.asList(new String[] {"T", "F", "U"}));
		
		a = new VariableNumMap(Arrays.asList(new Integer[] {0, 1, 3}), 
				Arrays.asList(new Variable<?>[] {v1, v2, v1}));
		b = new VariableNumMap(Arrays.asList(new Integer[] {2, 1}), 
				Arrays.asList(new Variable<?>[] {v1, v2}));
		// Note that c has conflicting assignments for variable v1!!
		c = new VariableNumMap(Arrays.asList(new Integer[] {2, 1}), 
				Arrays.asList(new Variable<?>[] {v1, v1}));
	}
	
	public void testImmutability() {
		List<Integer> inds = new ArrayList<Integer>(Arrays.asList(new Integer[] {1, 2, 3}));
		List<Variable<?>> vars= new ArrayList<Variable<?>>(Arrays.asList(new Variable<?>[] {v1,v2,v2}));
		VariableNumMap c = new VariableNumMap(inds, vars);
		inds.add(4);
		vars.add(v1);
		assertFalse(c.containsVariableNum(4));
		assertEquals(3, c.getVariableNums().size());
		assertEquals(3, c.getVariables().size());
	}
	
	public void testGetVariableNums() {
		assertEquals(Arrays.asList(new Integer[] {0, 1, 3}),
				a.getVariableNums());
		// Ensure that the returned values come in sorted order.
		assertEquals(Arrays.asList(new Integer[] {1, 2}),
				b.getVariableNums());
	}
	
	public void testGetVariables() {
		assertEquals(Arrays.asList(new Variable<?>[] {v1,v2,v1}),
				a.getVariables());
		// Ensure that the returned values come in sorted order.
		assertEquals(Arrays.asList(new Variable<?>[] {v2,v1}),
				b.getVariables());	
	}
	
	public void testIntersection() {
		VariableNumMap intersection = a.intersection(b);

		assertEquals(1, intersection.size());
		assertTrue(intersection.containsVariableNum(1));
		assertEquals(v2, intersection.getVariable(1));
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
		assertEquals(Arrays.asList(new Variable<?>[] {v1,v1}), result.getVariables());		
	}
	
	public void testRemoveAllError() {
		try {
			VariableNumMap result = a.removeAll(c);
		} catch (IllegalArgumentException e) {
			return;
		}
		fail("Expected IllegalArgumentException");
	}
	
	public void testUnion() {
		VariableNumMap result = a.union(b);
		assertEquals(Arrays.asList(new Integer[] {0,1,2,3}), result.getVariableNums());
		assertEquals(Arrays.asList(new Variable<?>[] {v1,v2,v1,v1}), result.getVariables());
	}
	
	public void testUnionError() {
		try {
			VariableNumMap result = a.union(c);
		} catch (IllegalArgumentException e) {
			return;
		}
		fail("Expected IllegalArgumentException");
	}
}
