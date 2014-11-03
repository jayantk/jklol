package com.jayantkrish.jklol.evaluation;

import java.util.List;

import com.google.common.base.Function;
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
public class ForwardingPredictor<I1, O1, I2, O2> extends AbstractPredictor<I1, O1> {

  private final Predictor<I2, O2> predictor;
  private final Function<I1, I2> inputConverter;
  private final Converter<O1, O2> outputConverter;

  /**
   * Gets a {@code Predictor} which uses {@code inputConverter} and
   * {@code outputConverter} to forward method invocations to {@code predictor}.
   * 
   * @param predictor
   * @param inputConverter
   * @param outputConverter
   */
  public ForwardingPredictor(Predictor<I2, O2> predictor, Function<I1, I2> inputConverter, 
      Converter<O1, O2> outputConverter) {
    this.predictor = predictor;
    this.inputConverter = inputConverter;
    this.outputConverter = outputConverter;
  }
  
  public static <I1, O1, I2, O2> ForwardingPredictor<I1, O1, I2, O2> create(
      Predictor<I2, O2> predictor, Function<I1, I2> inputConverter, 
      Converter<O1, O2> outputConverter) {
    return new ForwardingPredictor<I1, O1, I2, O2>(predictor, inputConverter, outputConverter);
  }
  
  public Prediction<I1, O1> getBestPredictions(I1 input, O1 output, int numPredictions) {
    Prediction<I2, O2> prediction = predictor.getBestPredictions(inputConverter.apply(input), 
        outputConverter.apply(output), numPredictions);
    
    List<O1> convertedPredictions = Lists.newArrayList();
    for (O2 outputPrediction : prediction.getPredictions()) {
      convertedPredictions.add(outputConverter.invert(outputPrediction));
    }
    
    return Prediction.create(input, output, prediction.getOutputScore(), 
        prediction.getScores(), convertedPredictions);
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
  
  public Function<I1, I2> getInputConverter() {
    return inputConverter;
  }
  
  public Converter<O1, O2> getOutputConverter() {
    return outputConverter;
  }
}
