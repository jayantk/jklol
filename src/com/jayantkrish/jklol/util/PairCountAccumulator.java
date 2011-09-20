package com.jayantkrish.jklol.util;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Maps;

/**
 * Helper class for counting the number of times pairs of objects occur.
 * 
 * @author jayantk
 */
public class PairCountAccumulator<A, B> {
  
  private final Map<A, Map<B, Double>> counts;
  
  public PairCountAccumulator() {
    counts = Maps.newHashMap();
  }
  
  public void incrementOutcome(A first, B second, double amount) {
    if (!counts.containsKey(first)) {
      counts.put(first, Maps.<B, Double>newHashMap());
    }
    if (!counts.get(first).containsKey(second)) {
      counts.get(first).put(second, 0.0);
    }
    counts.get(first).put(second, amount + counts.get(first).get(second));
  }
  
  public double getCount(A first, B second) {
    if (counts.containsKey(first) && counts.get(first).containsKey(second)) {
      return counts.get(first).get(second);
    }
    return 0.0;
  }
  
  public Set<B> getValues(A first) {
    if (counts.containsKey(first)) {
      return counts.get(first).keySet();
    }
    return Collections.emptySet();
  }
  
  public Set<A> keySet() {
    return counts.keySet();
  }
}
