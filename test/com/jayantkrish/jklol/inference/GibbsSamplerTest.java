import junit.framework.TestCase;

import com.jayantkrish.jklol.inference.GibbsSampler;
import com.jayantkrish.jklol.models.FactorGraph;

/**
 * Tests for GibbsSampler
 * @author jayant
 *
 */
public class GibbsSamplerTest extends TestCase {

	FactorGraph f;
	FactorGraph f2;
	
	GibbsSampler s;
	GibbsSampler s2;

	public void setUp() {
		f = InferenceTestCase.testFactorGraph1();
		s = new GibbsSampler(1000, 100, 10);
		s.setFactorGraph(f);
		
		f2 = InferenceTestCase.testFactorGraph2();
		s2 = new GibbsSampler(10000, 1000, 10);
		s2.setFactorGraph(f2);
	}

	public void testMarginals() {
		MarginalTestCase test1 = InferenceTestCase.testFactorGraph2Marginals2();
		test1.testMarginal(s2, 0.0);
	}
	
}
