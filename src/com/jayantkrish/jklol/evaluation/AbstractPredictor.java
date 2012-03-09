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
    return getBestPrediction(input);
  }
}
