package com.jayantkrish.jklol.util;

import com.google.common.base.Function;

/**
 * A converter is a means for translating between two equivalent representations
 * of an object. It is roughly equivalent to an invertible function. The
 * expected contract of a converter is that calling apply followed by invert
 * should return the "same" object. This requirement may not be satisfied in
 * some cases.
 * 
 * @param <U> first type to convert between
 * @param <V> second type to convert between
 * @author jayantk
 */
public abstract class Converter<U, V> implements Function<U, V> {
  // TODO(jayantk): It looks like a very similar interface will appear in
  // the guava collections soon.

  /**
   * Converts {@code item} from type U to V.
   * 
   * @param item
   * @return
   */
  @Override
  public abstract V apply(U item);

  /**
   * Converts {@code item} from type V to U.
   * 
   * @param item
   * @return
   */
  public abstract U invert(V item);

  /**
   * Gets a converter which is the inverse of {@code this}. Its {@code apply()}
   * method is equivalent to {@code this.invert()}, and vice versa.
   * 
   * @return
   */
  public Converter<V, U> inverse() {
    return new InverseConverter<V, U>(this);
  }

  /**
   * Returns a converter which applies {@code this} followed by {@code other}.
   * 
   * @param other
   * @return
   */
  public <W> Converter<U, W> compose(Converter<V, W> other) {
    return new ComposedConverter<U, V, W>(this, other);
  }

  /**
   * Wrapper for creating the inverse of a given converter. The implementation
   * simply forwards the {@code apply} and {@code invert} methods to the
   * opposite method of the wrapped converter.
   * 
   * @author jayantk
   * @param <S> first type to convert between
   * @param <T> second type to convert between
   */
  private class InverseConverter<S, T> extends Converter<S, T> {

    private final Converter<T, S> converter;

    /**
     * Creates a converter which is the inverse of {@code converter}.
     * 
     * @param converter
     */
    public InverseConverter(Converter<T, S> converter) {
      this.converter = converter;
    }

    @Override
    public T apply(S item) {
      return converter.invert(item);
    }

    @Override
    public S invert(T item) {
      return converter.apply(item);
    }

    @Override
    public Converter<T, S> inverse() {
      return converter;
    }
  }

  /**
   * A converter which composes the operations of two other converters.
   * 
   * @author jayantk
   * @param <A>
   * @param <B>
   * @param <C>
   */
  private class ComposedConverter<A, B, C> extends Converter<A, C> {

    private final Converter<A, B> first;
    private final Converter<B, C> second;

    /**
     * Creates a converter which applies {@code first} followed by
     * {@code second}.
     * 
     * @param first
     * @param second
     */
    public ComposedConverter(Converter<A, B> first, Converter<B, C> second) {
      this.first = first;
      this.second = second;
    }

    @Override
    public C apply(A item) {
      return second.apply(first.apply(item));
    }

    @Override
    public A invert(C item) {
      return first.invert(second.invert(item));
    }
  }
}
