package com.jayantkrish.jklol.util;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;

/**
 * Counts (fractional) occurrences of objects of type {@code T}. This class is
 * similar to {@link Multimap}, except that occurrence counts can be fractional.
 * A common use of this class is to estimate a multinomial distribution over an
 * unknown set of objects.
 * 
 * @author jayantk
 */
public class CountAccumulator<T> implements Serializable {
  private static final long serialVersionUID = 1L;

  private final DefaultHashMap<T, Double> counts;
  private double totalCount;

  /**
   * See {@link #create()}.
   */
  private CountAccumulator() {
    counts = new DefaultHashMap<T, Double>(0.0);
    totalCount = 0.0;
  }

  /**
   * Creates an accumulator with a count of 0 for all T, without requiring a
   * type argument.
   * 
   * @return
   */
  public static <T> CountAccumulator<T> create() {
    return new CountAccumulator<T>();
  }

  /**
   * Increments the occurrence count for {@code item} by {@code amount}.
   * 
   * @param item
   * @param amount
   */
  public void increment(T item, double amount) {
    counts.put(item, counts.get(item) + amount);
    totalCount += amount;
  }

  /**
   * Increments the occurrence count of each key in {@code amounts} by its
   * corresponding value.
   * 
   * @param amounts
   */
  public void increment(Map<? extends T, Double> amounts) {
    for (Map.Entry<? extends T, Double> entry : amounts.entrySet()) {
      increment(entry.getKey(), entry.getValue());
    }
  }

  /**
   * Increments the occurrence count (in {@code this}) of each item in
   * {@code amounts} by its corresponding count.
   * 
   * @param amounts
   */
  public void increment(CountAccumulator<? extends T> amounts) {
    increment(amounts.getCountMap());
  }
  
  public void multiply(T item, double amount) {
    double originalCount = counts.get(item);
    counts.put(item, originalCount * amount);
    totalCount += (originalCount * amount) - originalCount;
  }

  /**
   * Gets the occurrence count associated with {@code item}.
   * 
   * @param item
   * @return
   */
  public double getCount(T item) {
    return counts.get(item);
  }

  /**
   * Gets the empirical probability of {@code item}, that is
   * {@code getCount(item) / getTotalCount()}.
   * 
   * @param item
   * @return
   */
  public double getProbability(T item) {
    return getCount(item) / getTotalCount();
  }

  /**
   * Gets the sum of occurrence counts for all items in {@code this}.
   * 
   * @return
   */
  public double getTotalCount() {
    return totalCount;
  }

  /**
   * Gets all items T which have been observed.
   * 
   * @return
   */
  public Set<T> keySet() {
    return counts.keySet();
  }

  /**
   * Gets the counts in {@code this} as a {@code Map}. Each observed item is a
   * key in the returned map, and its occurrence count is the corresponding
   * value.
   * 
   * @return
   */
  public Map<T, Double> getCountMap() {
    return counts.getBaseMap();
  }

  /**
   * Gets the probabilities of the items in {@code this} as a {@code Map}. Each
   * observed item is a key in the returned map, and its probability is the
   * corresponding value.
   * 
   * @return
   */
  public Map<T, Double> getProbabilityMap() {
    Map<T, Double> probabilityMap = Maps.newHashMap();
    for (T item : counts.keySet()) {
      probabilityMap.put(item, getProbability(item));      
    }
    return probabilityMap;
  }
  
  
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((counts == null) ? 0 : counts.hashCode());
    long temp;
    temp = Double.doubleToLongBits(totalCount);
    result = prime * result + (int) (temp ^ (temp >>> 32));
    return result;
  }

  @SuppressWarnings("rawtypes")
  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    CountAccumulator other = (CountAccumulator) obj;
    if (counts == null) {
      if (other.counts != null)
        return false;
    } else if (!counts.equals(other.counts))
      return false;
    if (Double.doubleToLongBits(totalCount) != Double.doubleToLongBits(other.totalCount))
      return false;
    return true;
  }
}
