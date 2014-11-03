package com.jayantkrish.jklol.util;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;

/**
 * Helper class for counting the number of times pairs of objects occur.
 * 
 * @author jayantk
 */
public class PairCountAccumulator<A, B> implements Serializable {
  private static final long serialVersionUID = 1L;

  private final Map<A, Map<B, Double>> counts;
  private final DefaultHashMap<A, Double> conditionalCounts;
  private double totalCount;

  /**
   * Creates an accumulator with a count of 0 for every (A, B) pair.
   */
  public PairCountAccumulator() {
    counts = Maps.newHashMap();
    conditionalCounts = new DefaultHashMap<A, Double>(0.0);
    totalCount = 0.0;
  }

  /**
   * Creates an accumulator with a count of 0 for every (A, B) pair.
   * 
   * @return
   */
  public static <A, B> PairCountAccumulator<A, B> create() {
    return new PairCountAccumulator<A, B>();
  }

  /**
   * Increments the count of outcome (first, second) by {@code amount}.
   */
  public void incrementOutcome(A first, B second, double amount) {
    if (!counts.containsKey(first)) {
      counts.put(first, Maps.<B, Double> newHashMap());
    }
    if (!counts.get(first).containsKey(second)) {
      counts.get(first).put(second, 0.0);
    }
    counts.get(first).put(second, amount + counts.get(first).get(second));
    conditionalCounts.put(first, amount + conditionalCounts.get(first));
    totalCount += amount;
  }

  /**
   * Gets the number of times (first, second) has occurred.
   */
  public double getCount(A first, B second) {
    if (counts.containsKey(first) && counts.get(first).containsKey(second)) {
      return counts.get(first).get(second);
    }
    return 0.0;
  }

  /**
   * Gets the total number of times {@code first} has been observed with any
   * other value.
   * 
   * @param first
   * @return
   */
  public double getTotalCount(A first) {
    return conditionalCounts.get(first);
  }

  /**
   * Gets the total number of observed outcomes.
   */
  public double getTotalCount() {
    return totalCount;
  }

  /**
   * Gets the joint probability of observing (first, second). The sum of the
   * return value of this method over all (A, B) pairs is 1.0.
   */
  public double getProbability(A first, B second) {
    return getCount(first, second) / totalCount;
  }

  /**
   * Gets the outcome B with the highest conditional probability given
   * {@code first}. Returns {@code null} if {@code first} has never been
   * observed.
   * 
   * @param first
   * @return
   */
  public B getMostProbableOutcome(A first) {
    B best = null;
    double probability = -1.0;
    for (B second : getValues(first)) {
      if (getProbability(first, second) > probability) {
        probability = getProbability(first, second);
        best = second;
      }
    }
    return best;
  }

  /**
   * Gets a list of the possible outcomes given {@code first}, sorted in order
   * from most to least probable.
   * 
   * @param first
   * @return
   */
  public List<B> getOutcomesByProbability(A first) {
    if (counts.containsKey(first)) {
      Map<B, Double> sortedMap = MapUtils.reverse(MapUtils.sortByValue(counts.get(first)));
      return Lists.newArrayList(sortedMap.keySet());
    }
    return Collections.emptyList();
  }

  /**
   * Gets the conditional probability of observing {@code second} given
   * {@code first}. Returns NaN if {@code first} has never been observed.
   * 
   * @param first
   * @param second
   * @return
   */
  public double getConditionalProbability(A first, B second) {
    return getCount(first, second) / conditionalCounts.get(first);
  }

  /**
   * Gets all B values which have been observed with {@code first}.
   * 
   * @param first
   * @return
   */
  public Set<B> getValues(A first) {
    if (counts.containsKey(first)) {
      return counts.get(first).keySet();
    }
    return Collections.emptySet();
  }

  /**
   * Returns a {@code Multimap} containing all key value pairs with nonzero
   * count in this accumulator.
   * 
   * @return
   */
  public SetMultimap<A, B> getKeyValueMultimap() {
    SetMultimap<A, B> map = HashMultimap.create();
    for (A key : keySet()) {
      for (B value : getValues(key)) {
        map.put(key, value);
      }
    }
    return map;
  }

  /**
   * Gets all A values which have been observed.
   * 
   * @return
   */
  public Set<A> keySet() {
    return counts.keySet();
  }

  @Override
  public String toString() {
    return counts.toString();
  }
}
