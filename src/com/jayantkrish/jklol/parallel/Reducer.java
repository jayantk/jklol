package com.jayantkrish.jklol.parallel;

public interface Reducer<B> {

  /**
   * Gets a value used as the initial value for the accumulator. The returned
   * value should be the identity element for type {@code B}, as a single
   * reduce phase may accumulate this initial value multiple times. Therefore,
   * {@code this.reduce(x, this.getInitialValue())} should equal {@code x}.
   * 
   * @return
   */
  public B getInitialValue();

  /**
   * Returns the result of accumulating @{code item} and {@code accumulated}.
   * This method may mutate and return {@code accumulated}, which may be more
   * efficient than allocating and returning a new object.
   * 
   * @param item
   * @param accumulated
   * @return
   */
  public B reduce(B item, B accumulated);
}
