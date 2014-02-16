package com.jayantkrish.jklol.lisp;

import java.util.List;
import java.util.Map;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * A LISP cons cell, which is a tuple. Nested cons cells
 * are used to implement lists (essentially a linked list).
 * 
 * @author jayantk
 */
public class ConsValue {
  private final Object car;
  private final Object cdr;

  public ConsValue(Object car, Object cdr) {
    this.car = Preconditions.checkNotNull(car);
    this.cdr = Preconditions.checkNotNull(cdr);
  }

  /**
   * Gets the first element of this tuple.
   * 
   * @return
   */
  public Object getCar() {
    return car;
  }

  /**
   * Gets the second element of this tuple. Returns
   * {@code ConstantValue.NIL} if this is the last
   * element in a list.
   * 
   * @return
   */
  public Object getCdr() {
    return cdr;
  }

  /**
   * Returns {@code true} if this is a list, meaning it
   * is a nested sequence of cons cell terminated by NIL.
   *
   * @return
   */
  public boolean isList() {
    return ConstantValue.NIL.equals(cdr) ||
        ((cdr instanceof ConsValue) && ((ConsValue) cdr).isList());
  }

  public static <T> List<T> consListToList(Object consList, Class<T> clazz) {
    List<T> accumulator = Lists.newArrayList();
    consListToListHelper(consList, accumulator, clazz);
    return accumulator;
  }

  private static <T> void consListToListHelper(Object consList, List<T> accumulator, Class<T> clazz) {
    if (ConstantValue.NIL.equals(consList)) {
      return;
    } else {
      ConsValue consValue = (ConsValue) consList;
      accumulator.add(clazz.cast(consValue.getCar()));
      consListToListHelper(consValue.getCdr(), accumulator, clazz);
    }
  }

  public static Object listToConsList(List<?> list) {
    Object value = ConstantValue.NIL;
    for (int i = list.size() - 1; i >= 0; i--) {
      value = new ConsValue(list.get(i), value);
    }
    return value;
  }

  public static <K, V> Map<K, V> associationListToMap(Object consList, Class<K> keyClass, Class<V> valueClass) {
    List<Object> elements = consListToList(consList, Object.class);
    Map<K, V> map = Maps.newHashMap();
    for (Object element : elements) {
      List<Object> keyValue = consListToList(element, Object.class);
      map.put(keyClass.cast(keyValue.get(0)), valueClass.cast(keyValue.get(1)));
    }
    return map;
  }

  @Override
  public String toString() {
    if (isList()) {
      StringBuilder sb = new StringBuilder();
      sb.append("(list ");
      sb.append(Joiner.on(" ").join(consListToList(this, Object.class)));
      sb.append(")");
      return sb.toString();
    } else {
      return "(" + car.toString() + " " + cdr.toString() + ")";
    }
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((car == null) ? 0 : car.hashCode());
    result = prime * result + ((cdr == null) ? 0 : cdr.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    ConsValue other = (ConsValue) obj;
    if (car == null) {
      if (other.car != null)
        return false;
    } else if (!car.equals(other.car))
      return false;
    if (cdr == null) {
      if (other.cdr != null)
        return false;
    } else if (!cdr.equals(other.cdr))
      return false;
    return true;
  }
}
