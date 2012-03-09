package com.jayantkrish.jklol.parallel;

import java.util.Collection;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;

/**
 * Utilities for manipulating {@code Reducer}s and creating common
 * {@code Reducer}s.
 * 
 * @author jayantk
 */
public class Reducers {

  /**
   * Gets a {@code Reducer} which aggregates items of type {@code T} into a
   * collection of type {@code C}.
   * 
   * @param supplier
   * @return
   */
  public static <T, C extends Collection<T>> Reducer<T, C> getAggregatingReducer(
      Supplier<C> supplier) {
    return new AggregatingReducer<T, C>(supplier);
  }

  /**
   * Aggregates items of some type into a {@code Collection}.
   * 
   * @author jayantk
   * @param <T> item type to be aggregated
   * @param <C> collection type storing item
   */
  private static class AggregatingReducer<T, C extends Collection<T>> implements Reducer<T, C> {
    private final Supplier<C> constructor;

    public AggregatingReducer(Supplier<C> constructor) {
      this.constructor = Preconditions.checkNotNull(constructor);
    }

    @Override
    public C getInitialValue() {
      return constructor.get();
    }

    @Override
    public C reduce(T item, C accumulated) {
      accumulated.add(item);
      return accumulated;
    }

    @Override
    public C combine(C other, C accumulated) {
      other.addAll(accumulated);
      return other;
    }
  }
}