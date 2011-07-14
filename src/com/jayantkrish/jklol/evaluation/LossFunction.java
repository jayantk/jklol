package com.jayantkrish.jklol.evaluation;

/**
 * A LossFunction measures the quality of a Predictor's predictions.
 *
 * @param I the type of the input that the prediction is based on.
 * @param O - the type of the predicted object.
 */
public interface LossFunction<I, O> {

    public void accumulateLoss(Predictor<I, O> predictor, I input, O actual);

    /**
     * Accuracy is 1 - (Zero one loss), which assigns a "loss" of 1 when the prediction is .equals
     * to the actual, and 0 otherwise.
     *
     * @param O - the type of the prediction
     */
    public class Accuracy<I, O> implements LossFunction<I, O> {
	
	private int correct;
	private int total;

	public Accuracy() {
	    correct = 0;
	    total = 0;
	}

	@Override
	public void accumulateLoss(Predictor<I, O> predictor, I input, O actual) {
	    O prediction = predictor.getBestPrediction(input);
	    correct += actual.equals(prediction) ? 1.0 : 0.0;
	    total += 1;
	}

	public void getAccuracy() {
	    return ((double) correct) / total;
	}
    }

    /**
     * Measures precision, recall and accuracy.
     */
    public class PrecisionRecall<I> implements LossFunction<I, Boolean> {

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
     * Returns the loglikelihood using the natural log.
     */
    public class Loglikelihood<I, O> implements LossFunction<I, O> {

	private double loglikelihood;
	private int numExamples;

	public Loglikelihood() {
	    loglikelihood = 0.0;
	    numExamples = 0;
	}

	@Override
	public void accumulateLoss(Predictor<I, O> predictor, I input, O actual) {
	    loglikelihood += Math.log(predictor.getProbability(input, output));
	    numExamples += 1;
	}

	public double getAverageLoglikelihood() {
	    return loglikelihood / numExamples;
	}

	public double getPerplexity() {
	    return Math.exp(-1 * getAverageLoglikelihood())
	}
    }
}


