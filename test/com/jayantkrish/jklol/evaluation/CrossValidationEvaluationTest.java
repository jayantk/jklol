package com.jayantkrish.jklol.evaluation;

import java.util.Collection;
import java.util.List;

import junit.framework.TestCase;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.evaluation.LossFunctions.Accuracy;

/**
 * Unit tests for {@link CrossValidationEvaluation}
 * @author jayant
 *
 */
public class CrossValidationEvaluationTest extends TestCase {

	private String[][] fold1 = new String[][] {
			{"1", "true"}, {"2", "false"}, {"3", "false"},
			{"4", "true"}, {"5", "true"}
	};
	private String[][] fold2 = new String[][] {
			{"1", "true"}, {"2", "true"}, {"3", "true"},
			{"4", "false"}, {"5", "false"}
	};
	private String[][] fold3 = new String[][] {
			{"1", "false"}, {"2", "false"}, {"3", "false"},
			{"4", "false"}, {"5", "false"}
	};

	private Accuracy<String, String> accuracy;
	private CrossValidationEvaluation<String, String> evaluation;
	
	@Override
	public void setUp() {
		accuracy = LossFunctions.newAccuracy();
		List<Collection<Example<String, String>>> folds = Lists.newArrayList();
		folds.add(arrayToList(fold1));
		folds.add(arrayToList(fold2));
		folds.add(arrayToList(fold3));
		
		evaluation = new CrossValidationEvaluation<String, String>(folds);		
	}
	
	public void testEvaluateLoss() {
		evaluation.evaluateLoss(Baselines.<String, String>mostFrequentLabel(), accuracy);
		assertEquals(15, accuracy.getCount());
		assertEquals(4.0 / 15.0, accuracy.getAccuracy());
	}
	
	private List<Example<String, String>> arrayToList(String[][] data) {
		List<Example<String, String>> pairs = Lists.newArrayList();
		for (int i = 0; i < data.length; i++) {
			pairs.add(new Example<String, String>(data[i][0], data[i][1]));
		}
		return pairs;
	}
}
