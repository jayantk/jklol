package com.jayantkrish.jklol.evaluation;

import java.util.List;

import com.jayantkrish.jklol.util.Pair;

/**
 * TestSetEvaluation evaluates a predictor by training on one data set and testing on another.
 * @author jayant
 *
 * @param <I>
 * @param <O>
 */
public class TestSetEvaluation<I, O> extends AbstractEvaluation<I, O> {

	private Iterable<Pair<I, O>> trainingData;
	private Iterable<Pair<I, O>> testData;

	public TestSetEvaluation(Iterable<Pair<I, O>> trainingData,	Iterable<Pair<I, O>> testData) {
		this.trainingData = trainingData;
		this.testData = testData;
	}

	@Override
	public void evaluateLoss(PredictorTrainer<I, O> predictorTrainer, List<LossFunction<I, O>> lossFunctions) {
		Predictor<I, O> predictor = predictorTrainer.train(trainingData);
		for (Pair<I, O> testDatum : testData) {
			for (LossFunction<I, O> lossFunction : lossFunctions) {
				lossFunction.accumulateLoss(predictor, testDatum.getLeft(), testDatum.getRight());
			}
		}
	}
}
