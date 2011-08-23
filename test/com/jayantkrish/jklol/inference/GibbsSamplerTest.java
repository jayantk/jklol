import junit.framework.TestCase;

import com.jayantkrish.jklol.inference.GibbsSampler;
import com.jayantkrish.jklol.models.FactorGraph;

/**
 * Tests for GibbsSampler
 * @author jayant
 *
 */
public class GibbsSamplerTest extends TestCase {

	FactorGraph f2;
	GibbsSampler s2;

	public void setUp() {
		f2 = InferenceTestCase.testFactorGraph2();
		s2 = new GibbsSampler(1000, 1000, 1);
		s2.setFactorGraph(f2);
	}

	public void testMarginals() {
		MarginalTestCase test1 = InferenceTestCase.testFactorGraph2Marginals2();
		test1.testMarginal(s2, .05);
	}	
}
