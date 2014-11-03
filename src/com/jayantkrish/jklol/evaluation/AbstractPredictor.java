package com.jayantkrish.jklol.evaluation;

/**
 * Implementations of common {@code Predictor} methods.
 * 
 * @author jayantk
 * @param <I>
 * @param <O>
 */
public abstract class AbstractPredictor<I, O> implements Predictor<I, O> {

  @Override
  public O apply(I input) {
    return getBestPrediction(input).getBestPrediction();
  }
  
  @Override
  public Prediction<I, O> getBestPredictions(Example<? extends I, ? extends O> example, 
      int numPredictions) {
    return getBestPredictions(example.getInput(), example.getOutput(), numPredictions);
  }
  
  @Override
  public Prediction<I, O> getBestPrediction(I input, O output) {
    return getBestPredictions(input, output, 1);
  }
  
  @Override
  public Prediction<I, O> getBestPrediction(Example<? extends I, ? extends O> example) {
    return getBestPredictions(example.getInput(), example.getOutput(), 1);
  }
  
  @Override
  public Prediction<I, O> getBestPrediction(I input) {
    return getBestPredictions(input, null, 1);
  }
  
  @Override
  public double getScore(I input, O output) {
    Prediction<I, O> prediction = getBestPredictions(input, output, 1);
    return prediction.getOutputScore();
  }
}
