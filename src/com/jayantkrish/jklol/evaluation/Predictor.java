package com.jayantkrish.jklol.evaluation;

import java.util.List;

/**
 * A predictor wraps a prediction algorithm that can be used to predict values
 * of new instances. Typically instantiated using a {@link PredictorTrainer}.
 * 
 * @param I the type of the inputVar that the prediction is based on.
 * @param O the type of the outputVar prediction.
 */
public interface Predictor<I, O> {

  /**
   * Get the best prediction for the given input. Returns {@code null} if
   * {@code input} is invalid or no prediction can be computed for {@code input}
   * .
   */
  public O getBestPrediction(I input);

  /**
   * Get a ranked list of the numBest predictions for the given inputVar. If
   * fewer than {@code numBest} predictions can be retrieved, then as many
   * predictions as possible are returned. In this case, the returned list may
   * contain fewer than {@code numBest} items.
   * 
   * If no prediction can be computed for {@code input}, returns an empty list.
   */
  public List<O> getBestPredictions(I input, int numBest);

  /**
   * Get the probability of the outputVar given the inputVar.
   * 
   * If either {@code input} or {@code output} are invalid, returns 0.0.
   */
  public double getProbability(I input, O output);
}
