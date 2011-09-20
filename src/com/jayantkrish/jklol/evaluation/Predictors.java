package com.jayantkrish.jklol.evaluation;

import java.util.Map;

import com.google.common.collect.Maps;

/**
 * Static methods for instantiating common {@code Predictor}s.
 * 
 * @author jayantk
 */
public class Predictors {

  /**
   * Gets a {@code Predictor} that puts all of its probability mass on
   * {@code outputValue}.
   * 
   * @param outputValue
   * @return
   */
  public static <I, O> Predictor<I, O> constant(O outputValue) {
    Map<O, Double> outputProbabilities = Maps.newHashMap();
    outputProbabilities.put(outputValue, 1.0);
    return new ConstantPredictor<I, O>(outputProbabilities);
  }
}
