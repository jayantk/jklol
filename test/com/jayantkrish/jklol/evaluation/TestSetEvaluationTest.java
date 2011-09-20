package com.jayantkrish.jklol.evaluation;

import java.util.Collections;
import java.util.List;

import junit.framework.TestCase;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.evaluation.LossFunctions.Accuracy;

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
		evaluation = new TestSetEvaluation<String, String>(arrayToList(training),
		    Collections.<Example<String, String>>emptyList(), arrayToList(test));
		accuracy = LossFunctions.newAccuracy();
	}
	
	public void testEvaluateLoss() {
		evaluation.evaluateLoss(Baselines.<String, String>mostFrequentLabel(), accuracy);
		assertEquals(3.0 / 5.0, accuracy.getAccuracy());
	}
	
	private List<Example<String, String>> arrayToList(String[][] data) {
		List<Example<String, String>> pairs = Lists.newArrayList();
		for (int i = 0; i < data.length; i++) {
			pairs.add(new Example<String, String>(data[i][0], data[i][1]));
		}
		return pairs;
	}
}
