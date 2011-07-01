import java.util.Arrays;

import junit.framework.TestCase;

import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.FactorGraph;

public class JunctionTreeTest extends TestCase {

	FactorGraph f;
	FactorGraph f2;
	
	JunctionTree t;
	JunctionTree t2;

	public void setUp() {
		f = InferenceTestCase.testFactorGraph1();
		t = new JunctionTree();
		t.setFactorGraph(f);
		
		f2 = InferenceTestCase.testFactorGraph2();
		t2 = new JunctionTree();
		t2.setFactorGraph(f2);
	}

	public void testMarginals() {
		MarginalTestCase test1 = InferenceTestCase.testFactorGraph1Marginals1();
		test1.testMarginal(t, 0.0);
		
		MarginalTestCase test2 = InferenceTestCase.testFactorGraph1Marginals2();
		test2.testMarginal(t, 0.0);
	}

	public void testConditionals() {
		t.computeMarginals(f.outcomeToAssignment(Arrays.asList(new String[]{"Var2"}),
				Arrays.asList(new String[]{"F"})));

		DiscreteFactor m = (DiscreteFactor) t.getMarginal(Arrays.asList(new Integer[] {1}));
		assertEquals(12.0, m.getUnnormalizedProbability(Arrays.asList(new String[] {"foo"})));
		assertEquals(6.0, m.getUnnormalizedProbability(Arrays.asList(new String[] {"bar"})));

		m = (DiscreteFactor) t.getMarginal(Arrays.asList(new Integer[] {3, 4}));	
		assertEquals(18.0, m.getUnnormalizedProbability(Arrays.asList(new String[] {"F", "U"})));
		assertEquals(0.0, m.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "F"})));

		m = (DiscreteFactor) t.getMarginal(Arrays.asList(new Integer[] {0, 2, 3}));
		assertEquals(0.0, m.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "T", "T"})));
		assertEquals(6.0, m.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "F", "F"})));
		assertEquals(12.0, m.getUnnormalizedProbability(Arrays.asList(new String[] {"U", "F", "F"})));
	}

	public void testMaxMarginals() {
		t.computeMaxMarginals();

		DiscreteFactor m = (DiscreteFactor) t.getMarginal(Arrays.asList(new Integer[] {1}));	
		assertEquals(6.0,
				m.getUnnormalizedProbability(Arrays.asList(new String[] {"bar"})));
		assertEquals(9.0,
				m.getUnnormalizedProbability(Arrays.asList(new String[] {"foo"})));

		m = (DiscreteFactor) t.getMarginal(Arrays.asList(new Integer[] {0,2}));
		assertEquals(9.0,
				m.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "T"})));

		assertEquals(4.0,
				m.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "F"})));

		assertEquals(8.0,
				m.getUnnormalizedProbability(Arrays.asList(new String[] {"U", "F"})));

		assertEquals(0.0,
				m.getUnnormalizedProbability(Arrays.asList(new String[] {"U", "U"})));
	}

	public void testNonTreeStructured() {
		MarginalTestCase test = InferenceTestCase.testFactorGraph2Marginals2();
		test.testMarginal(t2, 0.0);
	}
}

