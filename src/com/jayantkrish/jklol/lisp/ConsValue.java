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
   * is a nested sequence of cons cells terminated by NIL.
   *
   * @return
   */
  public boolean isList() {
    Object ptr = cdr;
    while (ptr instanceof ConsValue) {
      ptr = ((ConsValue) ptr).cdr;
    }
    return ptr == ConstantValue.NIL;
  }

  public static <T> List<T> consListOrArrayToList(Object consList, Class<T> clazz) {
    if (consList instanceof Object[]) {
      List<T> list = Lists.newArrayList();
      Object[] array = (Object[]) consList;
      for (int i = 0; i < array.length; i++) {
        list.add(clazz.cast(array[i]));
      }
      return list;
    } else {
      return ConsValue.consListToList(consList, clazz);
    }
  }
  
  public static List<Object> consListToList(Object consList) {
    return consListToList(Object.class);
  }

  public static <T> List<T> consListToList(Object consList, Class<T> clazz) {
    List<T> accumulator = Lists.newArrayList();
    while (ConstantValue.NIL != consList) {
      ConsValue consValue = (ConsValue) consList;
      accumulator.add(clazz.cast(consValue.getCar()));
      consList = consValue.getCdr();
    }
    return accumulator;
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
