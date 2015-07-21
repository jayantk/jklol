package com.jayantkrish.jklol.evaluation;

import junit.framework.TestCase;


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
	  assertEquals(Math.log(0.6), predictor.getScore("1", "true"), .00001);
	  assertEquals(Math.log(0.6), predictor.getScore("foo", "true"), .00001);
	  assertEquals(Math.log(0.4), predictor.getScore("5", "false"), .00001);
	  assertEquals(Math.log(0.0), predictor.getScore("5", "NOTANOUTPUT"), .00001);
	  
	  assertEquals("true", predictor.getBestPrediction("1").getBestPrediction());
	  assertEquals("true", predictor.getBestPrediction("foo").getBestPrediction());
	  assertEquals("true", predictor.getBestPrediction("2").getBestPrediction());
	  
	  assertEquals("false", predictor.getBestPredictions("1", null, 2).getPredictions().get(1));
	  assertEquals("false", predictor.getBestPredictions("foo", null, 2).getPredictions().get(1));
	  assertEquals("false", predictor.getBestPredictions("2", null, 2).getPredictions().get(1));
	}
	
	public void testUniform() {
	  PredictorTrainer<String, String> trainer = Baselines.uniform();
	  Predictor<String, String> predictor = trainer.train(ExampleUtils.exampleArrayToList(training));
	  assertEquals(Math.log(0.5), predictor.getScore("1", "true"), .00001);  
	  assertEquals(Math.log(0.5), predictor.getScore("foo", "true"), .00001);
	  assertEquals(Math.log(0.5), predictor.getScore("5", "false"), .00001);
	  assertEquals(Math.log(0.0), predictor.getScore("5", "NOTANOUTPUT"), .00001);
	}
}
