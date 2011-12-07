package com.jayantkrish.jklol.evaluation;

/**
 * A training or test example in a data set. This interface is used for
 * evaluating prediction algorithms. Each example has an inputVar and outputVar; the
 * inputVar is provided to the prediction algorithm, which should then predict the
 * outputVar value given the inputVar. Both the inputVar and outputVar are provided to the
 * predictor during training.
 * 
 * @param <I> type of inputVar data to predictor
 * @param <O> type of outputVar data from predictor
 * @author jayantk
 */
public class Example<I, O> {

  private final I input;
  private final O output; 
  
  public Example(I input, O output) {
    this.input = input;
    this.output = output;
  }
  
  /**
   * Constructs an example from the given {@code inputVar} and {@code outputVar}.
   *  
   * @param inputVar
   * @param outputVar
   */
  public static <I, O> Example<I, O> create(I input, O output) {
    return new Example<I, O>(input, output);
  }
  
  public I getInput() {
    return input;
  }
  
  public O getOutput() {
    return output;
  }
      
  @Override
  public String toString() {
    return "(input=" + input + ", output=" + output + ")";
  }
}
