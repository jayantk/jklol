import java.util.*;
import com.jayantkrish.jklol.models.*;
import junit.framework.*;


public class SparseOutcomeTablePerformanceTest extends TestCase {


    SparseOutcomeTable<Double> table;
    List<Integer> varNums;

    public void setUp() {
	varNums = Arrays.asList(new Integer[] {0, 1, 2});
	table = new SparseOutcomeTable<Double>(varNums);
    }

    public void testAssignmentCreation() {
	System.out.println("testAssignmentCreation");
	long start = System.currentTimeMillis();

	for (int i = 0; i < 10000; i++) {
	    new Assignment(varNums, Arrays.asList(new Integer[] {(i / 100), (i / 10) % 10, i % 10}));
	}

	long elapsed = System.currentTimeMillis() - start;
	System.out.println("Elapsed: " + elapsed + " ms");
    }

    public void testPut() {
	System.out.println("testPut");
	long start = System.currentTimeMillis();

	for (int i = 0; i < 10000; i++) {
	    table.put(new Assignment(varNums, Arrays.asList(new Integer[] {(i / 100), (i / 10) % 10, i % 10})),
		    1.0);
	}

	long elapsed = System.currentTimeMillis() - start;
	System.out.println("Elapsed: " + elapsed + " ms");
    }

    public void testPutAssignmentSet() {
	System.out.println("testPutAssignmentSet");
	long start = System.currentTimeMillis();
	for (int i = 0; i < 10000; i++) {
		Assignment a = new Assignment(varNums, 
				Arrays.asList(new Integer[] {i / 100, (i / 10) % 10, i % 10}));
	    table.put(a, 1.0);
	}

	long elapsed = System.currentTimeMillis() - start;
	System.out.println("Elapsed: " + elapsed + " ms");
    }


    public void testPutSameAssignment() {
	System.out.println("testPutSameAssignment");
	Assignment a = new Assignment(varNums, Arrays.asList(new Integer[] {0, 0, 0}));
	long start = System.currentTimeMillis();

	for (int i = 0; i < 10000; i++) {
	    table.put(a, 1.0);
	}

	long elapsed = System.currentTimeMillis() - start;
	System.out.println("Elapsed: " + elapsed + " ms");
    }


    public void testIteration() {
	for (int i = 0; i < 10000; i++) {
	    table.put(new Assignment(varNums, Arrays.asList(new Integer[] {(i / 100), (i / 10) % 10, i % 10})),
		    1.0);
	}
	System.out.println("testIteration");
	long start = System.currentTimeMillis();

	Iterator<Assignment> iter = table.assignmentIterator();
	while (iter.hasNext()) {
	    iter.next();
	}

	long elapsed = System.currentTimeMillis() - start;
	System.out.println("Elapsed: " + elapsed + " ms");
    }

    public void testSubAssignment() {
	System.out.println("testSubAssignment");
	long start = System.currentTimeMillis();
	Assignment a = new Assignment(varNums, Arrays.asList(new Integer[] {0,0,0}));
	for (int i = 0; i < 10000; i++) {
	    a.subAssignment(Arrays.asList(new Integer[] {0, 2}));
	}

	long elapsed = System.currentTimeMillis() - start;
	System.out.println("Elapsed: " + elapsed + " ms");
    }
}