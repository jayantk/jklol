import java.util.List;

import junit.framework.TestCase;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.evaluation.Baselines;
import com.jayantkrish.jklol.evaluation.LossFunctions;
import com.jayantkrish.jklol.evaluation.TestSetEvaluation;
import com.jayantkrish.jklol.evaluation.LossFunctions.Accuracy;
import com.jayantkrish.jklol.util.Pair;

/**
 * Unit tests for {@link TestSetEvaluation}.
 * 
 * @author jayant
 */
public class TestSetEvaluationTest extends TestCase {

	private TestSetEvaluation<String, String> evaluation;
	private String[][] training = new String[][] {
			{"1", "true"}, {"2", "false"}, {"3", "false"},
			{"4", "true"}, {"5", "true"}
	};
	private String[][] test = new String[][] {
			{"1", "true"}, {"2", "true"}, {"3", "true"},
			{"4", "false"}, {"5", "false"}
	};
	
	private Accuracy<String, String> accuracy;
	
	@Override
	public void setUp() {		
		evaluation = new TestSetEvaluation<String, String>(
				arrayToList(training), arrayToList(test));
		accuracy = LossFunctions.newAccuracy();
	}
	
	public void testEvaluateLoss() {
		evaluation.evaluateLoss(new Baselines.MostFrequentLabel<String, String>(), accuracy);
		assertEquals(3.0 / 5.0, accuracy.getAccuracy());
	}
	
	private List<Pair<String, String>> arrayToList(String[][] data) {
		List<Pair<String, String>> pairs = Lists.newArrayList();
		for (int i = 0; i < data.length; i++) {
			pairs.add(new Pair<String, String>(data[i][0], data[i][1]));
		}
		return pairs;
	}
}
