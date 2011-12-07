package com.jayantkrish.jklol.evaluation;

import java.util.List;

import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.util.Converter;

/**
 * A {@link Predictor} which converts inputs and outputs into a different
 * format, then forwards them to an underlying predictor.
 * 
 * @author jayantk
 * @param <I1> inputVar type of this predictor
 * @param <O1> outputVar type of this predictor
 * @param <I2> inputVar type of underlying predictor
 * @param <O2> outputVar type of underlying predictor
 */
public class ForwardingPredictor<I1, O1, I2, O2> implements Predictor<I1, O1> {

  private final Predictor<I2, O2> predictor;
  private final Converter<I1, I2> inputConverter;
  private final Converter<O1, O2> outputConverter;

  /**
   * Gets a {@code Predictor} which uses {@code inputConverter} and
   * {@code outputConverter} to forward method invocations to {@code predictor}.
   * 
   * @param predictor
   * @param inputConverter
   * @param outputConverter
   */
  public ForwardingPredictor(Predictor<I2, O2> predictor, Converter<I1, I2> inputConverter,
      Converter<O1, O2> outputConverter) {
    this.predictor = predictor;
    this.inputConverter = inputConverter;
    this.outputConverter = outputConverter;
  }

  @Override
  public O1 getBestPrediction(I1 input) {
    return outputConverter.invert(
        predictor.getBestPrediction(inputConverter.apply(input)));
  }

  @Override
  public List<O1> getBestPredictions(I1 input, int numBest) {
    List<O2> predictions = predictor.getBestPredictions(inputConverter.apply(input), numBest);
    return Lists.newArrayList(Collections2.transform(predictions, outputConverter.inverse()));
  }

  @Override
  public double getProbability(I1 input, O1 output) {
    return predictor.getProbability(inputConverter.apply(input),
        outputConverter.apply(output));
  }

  /**
   * Gets the underlying predictor whose predictions are wrapped by {@code this}
   * predictor.
   * 
   * @return
   */
  public Predictor<I2, O2> getWrappedPredictor() {
    return predictor;
  }
  
  public Converter<I1, I2> getInputConverter() {
    return inputConverter;
  }
  
  public Converter<O1, O2> getOutputConverter() {
    return outputConverter;
  }
}
