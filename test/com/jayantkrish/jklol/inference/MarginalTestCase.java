import java.util.Arrays;
import java.util.List;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.inference.InferenceEngine;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.util.Assignment;
import junit.framework.Assert;

/**
 * A MarginalTestCase tests whether a facto
 * @author jayant
 *
 */
public class MarginalTestCase {

	private List<Integer> variables;
	private Assignment condition;
	private boolean maxMarginal;
	private List<Double> expectedProbs;
	private List<String[]> expectedVars;
	
	public MarginalTestCase(Integer[] variables, Assignment condition, boolean maxMarginal) {
		this.variables = Arrays.asList(variables);
		this.condition = condition;
		this.maxMarginal = maxMarginal;
		expectedProbs = Lists.newArrayList();
		expectedVars = Lists.newArrayList();
	}
	
	public void addTest(double expectedProb, String ... vars) {
		expectedProbs.add(expectedProb);
		expectedVars.add(vars);
	}
	
	public void testMarginal(InferenceEngine inference, double tolerance) {
		if (maxMarginal) {
			inference.computeMaxMarginals(condition);
		} else {
			inference.computeMarginals(condition);
		}
		DiscreteFactor f = (DiscreteFactor) inference.getMarginal(variables);
		for (int i = 0; i < expectedProbs.size(); i++) {
			Assert.assertEquals(expectedProbs.get(i), 
					f.getUnnormalizedProbability(Arrays.asList(expectedVars.get(i))));
		}
	}
}
