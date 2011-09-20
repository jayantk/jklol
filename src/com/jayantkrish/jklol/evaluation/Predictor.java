package com.jayantkrish.jklol.evaluation;

import java.util.List;

/**
 * A predictor wraps a prediction algorithm that can be used to predict values
 * of new instances. Typically instantiated using a {@link PredictorTrainer}
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
   * Get a ranked list of the numBest predictions for the given input. If fewer
   * than {@code numBest} predictions can be retrieved, then as many predictions
   * as possible are returned. In this case, the returned list may contain fewer
   * than {@code numBest} items.
   */
  public List<O> getBestPredictions(I input, int numBest);

  /**
   * Get the probability of the output given the input.
   */
  public double getProbability(I input, O output);

}
