package com.jayantkrish.jklol.evaluation;

import com.google.common.base.Preconditions;

/**
 * Implementations of some common loss functions.
 */
public class LossFunctions {

	/**
	 * Convenience method for instantiating an {@link Accuracy} loss function.
	 * @param <I>
	 * @param <O>
	 * @return
	 */
	public static <I, O> Accuracy<I, O> newAccuracy() {
		return new Accuracy<I, O>();
	}

	/**
	 * Convenience method for instantiating a {@link PrecisionRecall} loss function.
	 * @param <I>
	 * @return
	 */
	public static <I> PrecisionRecall<I> newPrecisionRecall() {
		return new PrecisionRecall<I>();
	}
	
	/**
	 * Convenience method for instantiating a {@link Loglikelihood} loss function.
	 * @param <I>
	 * @param <O>
	 * @return
	 */
	public static <I, O> Loglikelihood<I, O> newLoglikelihood() {
		return new Loglikelihood<I, O>();
	}

	private LossFunctions() {
		// Prevent instantiation.
	}
	
	/**
	 * Accuracy is 1 - (Zero one loss), which assigns a "loss" of 1 when the prediction is .equals
	 * to the actual, and 0 otherwise.
	 *
	 * @param O - the type of the prediction
	 */
	public static class Accuracy<I, O> implements LossFunction<I, O> {

		private int correct;
		private int total;

		public Accuracy() {
			correct = 0;
			total = 0;
		}

		@Override
		public void accumulateLoss(Predictor<I, O> predictor, I input, O actual) {
			Preconditions.checkNotNull(predictor);
			Preconditions.checkNotNull(input);
			Preconditions.checkNotNull(actual);

			O prediction = predictor.getBestPrediction(input);
			correct += actual.equals(prediction) ? 1.0 : 0.0;
			total += 1;
		}

		public double getAccuracy() {
			return ((double) correct) / total;
		}
		
		public int getCount() {
			return total;
		}
	}

	/**
	 * Measures precision, recall and accuracy.
	 */
	public static class PrecisionRecall<I> implements LossFunction<I, Boolean> {

		private int truePositives;
		private int trueNegatives;
		private int falsePositives;
		private int falseNegatives;

		public PrecisionRecall() {
			truePositives = 0;
			trueNegatives = 0;
			falsePositives = 0;
			falseNegatives = 0;
		}

		@Override
		public void accumulateLoss(Predictor<I, Boolean> predictor, I input, Boolean actual) {
			Preconditions.checkNotNull(predictor);
			Preconditions.checkNotNull(input);
			Preconditions.checkNotNull(actual);

			Boolean prediction = predictor.getBestPrediction(input);
			if (actual && prediction) {
				truePositives++;
			} else if (actual && !prediction) {
				falseNegatives++;
			} else if (!actual && prediction) {
				falsePositives++;
			} else {
				trueNegatives++;
			}
		}

		public double getPrecision() {
			return ((double) truePositives) / (truePositives + falsePositives);
		}

		public double getRecall() {
			return ((double) truePositives) / (truePositives + falseNegatives);
		}

		public double getAccuracy() {
			return ((double) truePositives + trueNegatives) / (truePositives + trueNegatives + falsePositives + falseNegatives);
		}
	}

	/**
	 * Evaluates the loglikelihood of a predictor's predictions, using the natural log.
	 */
	public static class Loglikelihood<I, O> implements LossFunction<I, O> {

		private double loglikelihood;
		private int numExamples;

		public Loglikelihood() {
			loglikelihood = 0.0;
			numExamples = 0;
		}

		@Override
		public void accumulateLoss(Predictor<I, O> predictor, I input, O actual) {
			Preconditions.checkNotNull(predictor);
			Preconditions.checkNotNull(input);
			Preconditions.checkNotNull(actual);

			loglikelihood += Math.log(predictor.getProbability(input, actual));
			numExamples += 1;
		}

		/**
		 * Get the average loglikelihood of the accumulated predictions.
		 * @return
		 */
		public double getAverageLoglikelihood() {
			return loglikelihood / numExamples;
		}

		/**
		 * Get the perplexity of the accumulated predictions.
		 * @return
		 */
		public double getPerplexity() {
			return Math.exp(-1 * getAverageLoglikelihood());
		}
	}
}
