package com.jayantkrish.jklol.evaluation;

/**
 * A training or test example in a data set. This interface is used for
 * evaluating prediction algorithms. Each example has an input and output; the
 * input is provided to the prediction algorithm, which should then predict the
 * output value given the input. Both the input and output are provided to the
 * predictor during training.
 * 
 * @param <I> type of input data to predictor
 * @param <O> type of output data from predictor
 * @author jayantk
 */
public class Example<I, O> {

  private final I input;
  private final O output; 
  
  public Example(I input, O output) {
    this.input = input;
    this.output = output;
  }
  
  public I getInput() {
    return input;
  }
  
  public O getOutput() {
    return output;
  }
  
  /**
   * Constructs an example from the given {@code input} and {@code output}.
   *  
   * @param input
   * @param output
   */
  public static <I, O> Example<I, O> create(I input, O output) {
    return new Example<I, O>(input, output);
  }
}
