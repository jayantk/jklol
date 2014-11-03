package com.jayantkrish.jklol.evaluation;


/**
 * A PredictorTrainer instantiates a {@link Predictor} based on training data.
 *
 * @param I the type of the inputVar that the prediction is based on.
 * @param O the type of the outputVar prediction.
 */
public interface PredictorTrainer<I, O> {

  public Predictor<I, O> train(Iterable<Example<I, O>> trainingData);
}
