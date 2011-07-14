package com.jayantkrish.jklol.evaluation;

import com.jayantkrish.jklol.util.Pair;

/**
 * A PredictorTrainer instantiates a {@link Predictor} based on training data.
 *
 * @param I the type of the input that the prediction is based on.
 * @param O the type of the output prediction.
 */
public interface PredictorTrainer<I, O> {

    public Predictor<I, O> train(Iterable<Pair<I, O>> trainingData);

}

