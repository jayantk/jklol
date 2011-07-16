package com.jayantkrish.jklol.evaluation;

/**
 * An Evaluation is a technique for computing the loss of a predictor.
 *
 * @param I the type of the input data that predictions are based on.
 * @param O the type of the output prediction.
 */
public interface Evaluation<I, O> {

    /**
     * Evaluate a particular prediction on a set of loss metrics. The result is a list of average
     * losses, in the same order as the loss functions.
     */
    public List<Double> evaluateLoss(PredictorTrainer<I, O> predictorTrainer, List<LossFunction<O>> lossFunctions);

}
