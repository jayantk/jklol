package com.jayantkrish.jklol.evaluation;

import junit.framework.TestCase;

import com.jayantkrish.jklol.testing.ExampleUtils;

/**
 * Unit tests for {@link Baselines}.
 * 
 * @author jayantk
 */
public class BaselinesTest extends TestCase {

	private String[][] training = new String[][] {
			{"1", "true"}, {"2", "false"}, {"3", "false"},
			{"4", "true"}, {"5", "true"}
	};
  
	public void testMostFrequentLabel() {
	  PredictorTrainer<String, String> trainer = Baselines.mostFrequentLabel();
	  Predictor<String, String> predictor = trainer.train(ExampleUtils.exampleArrayToList(training));
	  assertEquals(0.6, predictor.getProbability("1", "true"));
	  assertEquals(0.6, predictor.getProbability("foo", "true"));
	  assertEquals(0.4, predictor.getProbability("5", "false"));
	  assertEquals(0.0, predictor.getProbability("5", "NOTANOUTPUT"));
	  
	  assertEquals("true", predictor.getBestPrediction("1"));
	  assertEquals("true", predictor.getBestPrediction("foo"));
	  assertEquals("true", predictor.getBestPrediction("2"));
	  
	  assertEquals("false", predictor.getBestPredictions("1", 2).get(1));
	  assertEquals("false", predictor.getBestPredictions("foo", 2).get(1));
	  assertEquals("false", predictor.getBestPredictions("2", 2).get(1));
	}
	
	public void testUniform() {
	  PredictorTrainer<String, String> trainer = Baselines.uniform();
	  Predictor<String, String> predictor = trainer.train(ExampleUtils.exampleArrayToList(training));
	  assertEquals(0.5, predictor.getProbability("1", "true"));
	  assertEquals(0.5, predictor.getProbability("foo", "true"));
	  assertEquals(0.5, predictor.getProbability("5", "false"));
	  assertEquals(0.0, predictor.getProbability("5", "NOTANOUTPUT"));
	}
}
