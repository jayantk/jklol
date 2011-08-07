import junit.framework.TestCase;

import com.jayantkrish.jklol.inference.JunctionTree;

/**
 * Unit tests for {@link JunctionTree}.
 * 
 * @author jayant
 */
public class JunctionTreeTest extends TestCase {

	public void testBasicMarginals() {
		InferenceTestCase.testBasicUnconditional().runTest(new JunctionTree(), 0.0);
	}
	
	public void testNonTreeStructuredMarginals() {
		InferenceTestCase.testNonCliqueTreeUnconditional().runTest(new JunctionTree(), 0.0);
	}

	public void testConditionals() {
		InferenceTestCase.testBasicConditional().runTest(new JunctionTree(), 0.0);
	}

	public void testMaxMarginals() {
		InferenceTestCase.testBasicMaxMarginals().runTest(new JunctionTree(), 0.0);
	}
}

