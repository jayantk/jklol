package com.jayantkrish.jklol.evaluation;

import com.google.common.base.Function;
import com.jayantkrish.jklol.util.Converter;
import com.jayantkrish.jklol.util.Converters;

/**
 * A training or test example in a data set. This interface is used for
 * evaluating prediction algorithms. Each example has an inputVar and outputVar;
 * the inputVar is provided to the prediction algorithm, which should then
 * predict the outputVar value given the inputVar. Both the inputVar and
 * outputVar are provided to the predictor during training.
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
   * Constructs an example from the given {@code inputVar} and {@code outputVar}
   * .
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

  /**
   * Adapts {@code converter} to apply to the input field of {@code Example}s.
   * 
   * @param converter
   * @return
   */
  public static <A, B, C> Converter<Example<A, C>, Example<B, C>> inputConverter(Converter<A, B> converter) {
    return new ExampleConverter<A, C, B, C>(converter, Converters.<C> identity());
  }

  /**
   * Adapts {@code converter} to apply to the output field of {@code Example}s.
   * 
   * @param converter
   * @return
   */
  public static <A, B, C> Converter<Example<A, B>, Example<A, C>> outputConverter(Converter<B, C> converter) {
    return new ExampleConverter<A, B, A, C>(Converters.<A> identity(), converter);
  }

  /**
   * Creates a converter which applies {@code inputConverter} to the input of
   * each example, and {@code outputConverter} to the output.
   * 
   * @param converter
   * @return
   */
  public static <A, B, C, D> Converter<Example<A, B>, Example<C, D>> converter(
      Converter<A, C> inputConverter, Converter<B, D> outputConverter) {
    return new ExampleConverter<A, B, C, D>(inputConverter, outputConverter);
  }

  /**
   * Gets a function which maps {@code Example}s to their input value.
   * 
   * @return
   */
  public static <A> Function<Example<A, ?>, A> inputGetter() {
    return new Function<Example<A, ?>, A>() {
      @Override
      public A apply(Example<A, ?> item) {
        return item.getInput();
      }
    };
  }

  /**
   * Gets a function which maps {@code Example}s to their output value.
   * 
   * @return
   */
  public static <B> Function<Example<?, B>, B> outputGetter() {
    return new Function<Example<?, B>, B>() {
      @Override
      public B apply(Example<?, B> item) {
        return item.getOutput();
      }
    };
  }

  /**
   * Converter for the input and output fields of an {@code Example}.
   * 
   * @author jayantk
   * @param <A>
   * @param <B>
   * @param <C>
   * @param <D>
   */
  public static class ExampleConverter<A, B, C, D> extends Converter<Example<A, B>, Example<C, D>> {
    private final Converter<A, C> inputConverter;
    private final Converter<B, D> outputConverter;

    public ExampleConverter(Converter<A, C> inputConverter, Converter<B, D> outputConverter) {
      this.inputConverter = inputConverter;
      this.outputConverter = outputConverter;
    }

    @Override
    public Example<C, D> apply(Example<A, B> item) {
      return Example.create(inputConverter.apply(item.getInput()),
          outputConverter.apply(item.getOutput()));
    }

    @Override
    public Example<A, B> invert(Example<C, D> item) {
      return Example.create(inputConverter.invert(item.getInput()),
          outputConverter.invert(item.getOutput()));
    }
  }
}
