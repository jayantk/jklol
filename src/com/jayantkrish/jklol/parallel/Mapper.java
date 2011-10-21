package com.jayantkrish.jklol.parallel;

import com.google.common.base.Function;

/**
 * The first phase of a map-reduce pipeline, which performs elementwise data
 * processing on a collection (see {@link MapReduceExecutor}). Implementors of
 * this class should be thread-safe, as multiple threads may simultaneously
 * execute methods on a single instance. {@code Mapper}s should also be
 * stateless.
 * 
 * @author jayantk
 * @param <A>
 * @param <B>
 */
public abstract class Mapper<A, B> implements Function<A, B> {

  @Override
  public B apply(A item) {
    return map(item);
  }

  /**
   * Maps {@code item} to a value. This method should not be stateful: calling
   * {@code map} with the same items in a different order should return the same
   * values for each {@code item}.
   * 
   * @param item
   * @return
   */
  public abstract B map(A item);

  /**
   * Gets the identity mapper.
   * @return
   */
  public static <A> Mapper<A, A> identity() {
    return new Mapper<A, A>() {
      @Override
      public A map(A item) {
        return item;
      }
    };
  }
}
