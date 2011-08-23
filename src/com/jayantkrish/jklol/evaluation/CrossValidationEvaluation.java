package com.jayantkrish.jklol.evaluation;

/**
 * Runs cross validation to estimate the generalization error of a predictor.
 */
public class CrossValidationEvaluation<I, O> implements Evaluation<I, O> {

    private List<Iterable<Pair<I, O>>> folds;

    public CrossValidationEvaluation(List<Iterable<Pair<I, O>>> folds) {
	this.folds = folds;
    }

    @Override
    public void evaluateLoss(PredictorTrainer<I, O> predictorTrainer, List<LossFunction<I, O>> lossFunctions) {	
	for (int i = 0; i < folds.size(); i++) {
	    List<Iterable<Pair<I, O>>> trainingFolds = Lists.newArrayList(folds);
	    trainingFolds.remove(i);
	    Iterable<Pair<I, O>> testFold = folds.get(i);
	    TestSetEvaluation evaluation = new TestSetEvaluation(Iterables.concat(trainingFolds), testFold);

	    evaluation.evaluateLoss(predictorTrainer, lossFunctions);
	}
    }

    /**
     * Construct a cross validation evaluation from a data set by partitioning it into k folds.
     * The elements in data should be in a random order.
     */
    public static CrossValidationEvaluation kFold(Iterable<Pair<I, O>> data, int k) {
	Preconditions.checkNotNull(data);
	Preconditions.checkArgument(k > 1);

	int numTrainingPoints = Iterables.size(data);
	List<Iterable<Pair<I, O>>> folds = Lists.newArrayList(
		Iterables.partition(data, Math.ceil(numTrainingPoints / k)));
	return new CrossValidationEvaluation(folds);
    }
}
