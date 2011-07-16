package com.jayantkrish.jklol.evaluation;

/**
 * A LossFunction measures the quality of a Predictor's predictions.
 *
 * @param I the type of the input that the prediction is based on.
 * @param O - the type of the predicted object.
 */
public interface LossFunction<I, O> {

    public void accumulateLoss(Predictor<I, O> predictor, I input, O actual);

}

