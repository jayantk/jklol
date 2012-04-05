package com.jayantkrish.jklol.evaluation;

import com.jayantkrish.jklol.evaluation.Predictor.Prediction;

/**
 * A LossFunction measures the quality of a Predictor's predictions.
 *
 * @param I type of the inputVar that the prediction is based on.
 * @param O type of the predicted object.
 */
public interface LossFunction<I, O> {

  // LossFunction<I, O> emptyCopy();
  
  void accumulateLoss(Prediction<I, O> prediction);
}

