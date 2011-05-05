import com.jayantkrish.jklol.models.Assignment;

import junit.framework.*;

import java.util.*;

public class AssignmentTest extends TestCase {

    private Assignment a;
    private Assignment b;
    private Assignment c;

    private Assignment e;

    public void setUp() {
	a = new Assignment(Arrays.asList(new Integer[] {5, 1, 3, 0}),
		Arrays.asList(new Integer[] {6, 2, 4, 1}));
	b = new Assignment(Arrays.asList(new Integer[] {3, 7, 29}),
		Arrays.asList(new Integer[] {2, 4, 6}));
	c = new Assignment(Arrays.asList(new Integer[] {2, 4, 6}),
		Arrays.asList(new Integer[] {3, 5, 7}));

	e = new Assignment(Collections.EMPTY_LIST,
		Collections.EMPTY_LIST);
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
	    Assignment s = a.subAssignment(Arrays.asList(new Integer[] {5, 1, 179839}));
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
	    Assignment j = a.jointAssignment(b);
	} catch (RuntimeException e) {
	    return;
	}
	fail("Expected RuntimeException.");
    }

    public void testJointAssignmentEmpty() {
	Assignment j = a.jointAssignment(e);
	
	assertEquals(Arrays.asList(new Integer[] {0, 1, 3, 5}),
		j.getVarNumsSorted());
	
	assertEquals(Arrays.asList(new Integer[] {1, 2, 4, 6}),
		j.getVarValuesInKeyOrder());

    }

}