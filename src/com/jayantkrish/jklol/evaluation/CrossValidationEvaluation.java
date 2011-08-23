package com.jayantkrish.jklol.evaluation;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.util.Pair;

/**
 * Runs cross validation to estimate the generalization error of a predictor. 
 * The cross validation splits are re-used across multiple runs of this evaluation
 * to better compare algorithms (i.e., this enables using a paired t-test).
 * 
 * @param <I> input type of the predictor being evaluated.
 * @param <O> output type of the predictor being evaluated. 
 */
public class CrossValidationEvaluation<I, O> extends AbstractEvaluation<I, O> {

	private List<Iterable<Pair<I, O>>> folds;

	public CrossValidationEvaluation(List<Iterable<Pair<I, O>>> folds) {
		this.folds = folds;
	}

	@Override
	public void evaluateLoss(PredictorTrainer<I, O> predictorTrainer, 
			List<LossFunction<I, O>> lossFunctions) {	
		for (int i = 0; i < folds.size(); i++) {
			List<Iterable<Pair<I, O>>> trainingFolds = Lists.newArrayList(folds);
			trainingFolds.remove(i);
			Iterable<Pair<I, O>> testFold = folds.get(i);

			TestSetEvaluation<I, O> evaluation = new TestSetEvaluation<I, O>(Iterables.concat(trainingFolds), testFold);
			evaluation.evaluateLoss(predictorTrainer, lossFunctions);
		}
	}

	/**
	 * Construct a cross validation evaluation from a data set by partitioning it into k folds.
	 * The elements in data should be in a random order.
	 */
	public static <I, O> CrossValidationEvaluation<I, O> kFold(
			Iterable<Pair<I, O>> data, int k) {
		Preconditions.checkNotNull(data);
		Preconditions.checkArgument(k > 1);

		int numTrainingPoints = Iterables.size(data);
		List<Iterable<Pair<I, O>>> folds = Lists.newArrayList();
		for (List<Pair<I, O>> fold : Iterables.partition(data, (int) Math.ceil(numTrainingPoints / k))) {
			folds.add(fold);
		}
		
		return new CrossValidationEvaluation<I, O>(folds);
	}
}
