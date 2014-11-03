package com.jayantkrish.jklol.parallel;

import com.google.common.base.Function;

/**
 * Utility methods for {@code Mapper}s.
 * 
 * @author jayantk
 */
public class Mappers {

  /**
   * Gets a mapper which applies {@code function} to each element.
   * 
   * @param function
   * @return
   */
  public static <A, B> Mapper<A, B> fromFunction(final Function<A, B> function) {
    return new Mapper<A, B>() {
      @Override
      public B map(A item) {
        return function.apply(item);
      }
    };
  }

  /**
   * Gets the identity mapper.
   * 
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
