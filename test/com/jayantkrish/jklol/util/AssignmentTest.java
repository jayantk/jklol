import java.util.Arrays;

import junit.framework.TestCase;

import com.jayantkrish.jklol.util.Assignment;

public class AssignmentTest extends TestCase {

	private Assignment a;
	private Assignment b;
	private Assignment c;

	public void setUp() {
		a = new Assignment(Arrays.asList(new Integer[] {5, 1, 3, 0}),
				Arrays.asList(new Object[] {6, 2, 4, 1}));
		b = new Assignment(Arrays.asList(new Integer[] {3, 7, 29}),
				Arrays.asList(new Object[] {2, 4, 6}));
		c = new Assignment(Arrays.asList(new Integer[] {2, 4, 6}),
				Arrays.asList(new Object[] {3, 5, 7}));
	}

	public void testVarNumsSorted() {
		assertEquals(Arrays.asList(new Integer[] {0, 1, 3, 5}),
				a.getVarNumsSorted());

		assertEquals(Arrays.asList(new Integer[] {1, 2, 4, 6}),
				a.getVarValuesInKeyOrder());
	}

	public void testSubAssignment() {
		Assignment s = a.subAssignment(Arrays.asList(new Integer[] {5, 1}));
		assertEquals(Arrays.asList(new Integer[] {1, 5}),
				s.getVarNumsSorted());

		assertEquals(Arrays.asList(new Integer[] {2, 6}),
				s.getVarValuesInKeyOrder());
	}

	public void testSubAssignment2() {
		try {
			a.subAssignment(Arrays.asList(new Integer[] {5, 1, 179839}));
		} catch (AssertionError e) {
			return;
		}
		fail("Expected AssertionError.");
	}

	public void testJointAssignment() {
		Assignment j = a.jointAssignment(c);
		assertEquals(Arrays.asList(new Integer[] {0, 1, 2, 3, 4, 5, 6}),
				j.getVarNumsSorted());

		assertEquals(Arrays.asList(new Integer[] {1, 2, 3, 4, 5, 6, 7}),
				j.getVarValuesInKeyOrder());
	}

	public void testJointAssignmentError() {
		try {
			a.jointAssignment(b);
		} catch (RuntimeException e) {
			return;
		}
		fail("Expected RuntimeException.");
	}

	public void testJointAssignmentEmpty() {
		Assignment j = a.jointAssignment(Assignment.EMPTY);

		assertEquals(Arrays.asList(new Integer[] {0, 1, 3, 5}),
				j.getVarNumsSorted());

		assertEquals(Arrays.asList(new Integer[] {1, 2, 4, 6}),
				j.getVarValuesInKeyOrder());

	}

	public void testRemoveAll() {
		Assignment result = a.removeAll(Arrays.asList(new Integer[] {1,3,4}));
		assertEquals(Arrays.asList(new Integer[] {0,5}),
				result.getVarNumsSorted());

		result = a.removeAll(Arrays.asList(new Integer[] {}));
		assertEquals(Arrays.asList(new Integer[] {0,1,3,5}),
				result.getVarNumsSorted());
	}

}