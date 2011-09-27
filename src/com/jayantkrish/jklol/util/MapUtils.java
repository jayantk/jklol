package com.jayantkrish.jklol.util;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;

/**
 * Static utility methods for manipulating {@link Map}s.
 * 
 * @author jayantk
 */
public class MapUtils {

  /**
   * Sorts the entries in {@code map} into ascending order of their value. The
   * returned map has a predictable iteration order, from the entry with the
   * smallest value to the entry with the largest value.
   * 
   * @param map
   * @return
   */
  public static <A, B extends Comparable<B>> Map<A, B> sortByValue(Map<A, B> map) {
    List<Map.Entry<A, B>> list = Lists.newArrayList(map.entrySet());
    Collections.sort(list, new Comparator<Map.Entry<A, B>>() {
      public int compare(Map.Entry<A, B> o1, Map.Entry<A, B> o2) {
        return o1.getValue().compareTo(o2.getValue());
      }
    });
    return listToMap(list);
  }

  /**
   * Returns a map with the same keys as {@code map}, but the reverse iteration
   * order.
   * 
   * @param map
   * @return
   */
  public static <A, B> Map<A, B> reverse(Map<A, B> map) {
    List<Map.Entry<A, B>> list = Lists.newArrayList(map.entrySet());
    Collections.reverse(list);
    return listToMap(list);
  }

  /**
   * Puts the entries in {@code list} into a {@code Map}. The returned map has
   * the same iteration order over entries as {@code list}.
   * 
   * @param list
   * @return
   */
  private static <A, B> Map<A, B> listToMap(List<Map.Entry<A, B>> list) {
    Map<A, B> result = new LinkedHashMap<A, B>();
    for (Iterator<Map.Entry<A, B>> it = list.iterator(); it.hasNext();) {
      Map.Entry<A, B> entry = (Map.Entry<A, B>) it.next();
      result.put(entry.getKey(), entry.getValue());
    }
    return result;
  }

  private MapUtils() {
    // Prevent instantiation.
  }
}
