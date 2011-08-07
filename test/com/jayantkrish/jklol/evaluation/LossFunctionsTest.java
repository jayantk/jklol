import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.evaluation.Predictor;
import com.jayantkrish.jklol.evaluation.LossFunction;
import com.jayantkrish.jklol.evaluation.LossFunctions.Accuracy;
import com.jayantkrish.jklol.evaluation.LossFunctions.Loglikelihood;
import com.jayantkrish.jklol.evaluation.LossFunctions.PrecisionRecall;

public class LossFunctionsTest extends TestCase {

	private Map<String, Boolean> examples;
	private Predictor<String, Boolean> testPredictor;

	private Accuracy<String, Boolean> accuracy;
	private PrecisionRecall<String> precisionRecall;
	private Loglikelihood<String, Boolean> loglikelihood;

	public void setUp() {
		Map<String, Boolean> predictions = Maps.newHashMap();
		predictions.put("foo", true);
		predictions.put("bar1", true);
		predictions.put("bar2", true);
		predictions.put("baz", false);
		predictions.put("boo1", false);
		predictions.put("boo2", true);

		examples = Maps.newHashMap();
		examples.put("foo", true);
		examples.put("bar1", false);
		examples.put("bar2", false);
		examples.put("baz", true);
		examples.put("boo1", false);
		examples.put("boo2", false);

		testPredictor = new TestPredictor<String>(predictions, 0.9);

		accuracy = new Accuracy<String, Boolean>();
		precisionRecall = new PrecisionRecall<String>();
		loglikelihood = new Loglikelihood<String, Boolean>();
	}

	public void testAccuracy() {
		accumulateLoss(accuracy, testPredictor);
		assertEquals(1.0 / 3.0, accuracy.getAccuracy());
	}

	public void testPrecisionRecall() {
		accumulateLoss(precisionRecall, testPredictor);
		assertEquals(1.0 / 4.0, precisionRecall.getPrecision());
		assertEquals(1.0 / 2.0, precisionRecall.getRecall());
		assertEquals(1.0 / 3.0, precisionRecall.getAccuracy());
	}

	public void testLoglikelihood() {
		accumulateLoss(loglikelihood, testPredictor);
		assertEquals((2 * Math.log(0.9) + 4 * Math.log(0.1)) / 6.0, 
				loglikelihood.getAverageLoglikelihood(), .0000001);
		assertEquals(Math.exp(-1.0 * (2 * Math.log(0.9) + 4 * Math.log(0.1)) / 6.0), 
				loglikelihood.getPerplexity(), .0000001);
	}

	private void accumulateLoss(LossFunction<String, Boolean> loss, 
			Predictor<String, Boolean> predictor) {
		for (Map.Entry<String, Boolean> example : examples.entrySet()) {
			loss.accumulateLoss(predictor, example.getKey(), example.getValue());
		}
	}

	/*
	 * Memorizes a true or false prediction for each data point.
	 */
	private class TestPredictor<I> implements Predictor<I, Boolean> {

		private Map<I, Boolean> predictions;
		private double probability;

		public TestPredictor(Map<I, Boolean> predictions, double probability) {
			this.predictions = predictions;
			this.probability = probability;
		}

		@Override
		public Boolean getBestPrediction(I input) {
			return predictions.get(input);
		}

		@Override
		public double getProbability(I input, Boolean output) {
			if (predictions.get(input).equals(output)) {
				return probability;
			}
			return 1.0 - probability;
		}

		@Override
		public List<Boolean> getBestPredictions(I input, int numBest) {
			return Lists.newArrayList(predictions.get(input), !predictions.get(input));
		}

	}

}