package com.jayantkrish.jklol.util;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/**
 * Static utility methods for manipulating {@link Converter}s.
 * 
 * @author jayantk
 */
public class Converters {

  /**
   * Gets the identity converter, whose {@code apply} and {@code invert}
   * functions simply return their argument.
   * 
   * @return
   */
  public static <U> Converter<U, U> identity() {
    return new IdentityConverter<U>();
  }

  /**
   * Gets a converter which wraps a converter taking a {@code List} with only
   * one element and eliminates the need for the {@code List}.
   * 
   * @param converter
   * @return
   */
  public static <U, V> Converter<U, V> wrapSingletonList(Converter<List<U>, V> converter) {
    Converter<U, List<U>> listConverter = new SingletonListConverter<U>().inverse();
    return listConverter.compose(converter);
  }

  /**
   * Wraps {@code converter} such that its inputVar is of type {@code V}. The
   * result of invert on the returned converter is also cast to {@code V} before
   * being returned.
   * 
   * @param converter
   * @param classToCast
   * @return
   */
  public static <U, V> Converter<V, U> wrapWithCast(
      Converter<Object, U> converter, Class<V> type) {
    Converter<V, Object> castConverter = new CastingConverter<V>(type).inverse();
    return castConverter.compose(converter);
  }
  
  /**
   * The identity function.
   * 
   * @author jayantk
   * @param <T>
   */
  public static class IdentityConverter<T> extends Converter<T, T> {
    @Override
    public T apply(T arg) {
      return arg;
    }
    
    @Override
    public T invert(T arg) {
      return arg;
    }
  }

  /**
   * Gets a converter which wraps and unwraps an item in a {@code List}.
   * 
   * @author jayantk
   * @param <T>
   */
  public static class SingletonListConverter<T> extends Converter<List<T>, T> {

    @Override
    public T apply(List<T> item) {
      Preconditions.checkArgument(item.size() == 1);
      return item.get(0);
    }

    @Override
    public List<T> invert(T item) {
      List<T> items = Lists.newArrayList();
      items.add(item);
      return items;
    }
  }

  /**
   * Casts the inputs to type {@code V}.
   * 
   * @author jayantk
   * @param <V>
   */
  public static class CastingConverter<V> extends Converter<Object, V> {

    private Class<V> cast;

    public CastingConverter(Class<V> cast) {
      this.cast = cast;
    }

    @Override
    public V apply(Object item) {
      return cast.cast(item);
    }

    @Override
    public Object invert(V item) {
      return item;
    }
  }

  private Converters() {
    // Prevent instantiation.
  }
}
