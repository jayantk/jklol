package com.jayantkrish.jklol.evaluation;

import com.jayantkrish.jklol.util.Pair;

/**
 * A predictor wraps a prediction algorithm that can be used to predict
 * values of new instances. Typically instantiated using a {@link PredictorTrainer}
 *
 * @param I the type of the input that the prediction is based on.
 * @param O the type of the output prediction.
 */
public interface Predictor<I, O> {

    /**
     * Get the best prediction for the given input.
     */
    public O getBestPrediction(I input);

    /**
     * Get a ranked list of the numBest predictions for the given input.
     */
    public List<O> getBestPredictions(I input, int numBest);

    /**
     * Get the probability of the output given the input.
     */
    public double getProbability(I input, O output);

}
