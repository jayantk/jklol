package com.jayantkrish.jklol.parallel;

/**
 * Second half of a map-reduce pipeline that compiles the output of the {@code
 * Mapper}s into a return value.
 * 
 * @author jayantk
 * @param <B> input type, which is the output type of the {@code Mapper} in the
 * first half of the pipeline
 * @param <C> output type of the reducer.
 */
public interface Reducer<B, C> {

  /**
   * Gets a value used as the initial value for the accumulator. The returned
   * value should be the identity element for type {@code C}, as a single reduce
   * phase may accumulate this initial value multiple times. Therefore,
   * {@code this.reduce(x, this.getInitialValue())} should equal {@code x}.
   * 
   * @return
   */
  public C getInitialValue();

  /**
   * Returns the result of accumulating {@code item} and {@code accumulated}.
   * This method may mutate and return {@code accumulated}, which may be more
   * efficient than allocating and returning a new object.
   * 
   * @param item
   * @param accumulated
   * @return
   */
  public C reduce(B item, C accumulated);

  /**
   * Combines the accumulated results in {@code other} and {@code accumulated},
   * returning the result. This method may mutate and return either
   * {@code other} or {@code accumulated} , which may be more efficient than
   * allocating and returning a new object.
   * 
   * @param other
   * @param accumulated
   * @return
   */
  public C combine(C other, C accumulated);

  /**
   * Implementation of {@code Reducer} where both the return type and the type
   * being accumulated are the same.
   * 
   * @author jayantk
   * @param <B>
   */
  public abstract class SimpleReducer<B> implements Reducer<B, B> {

    @Override
    public B combine(B other, B accumulated) {
      return reduce(other, accumulated);
    }
  }
}
