package com.jayantkrish.jklol.util;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;

public class Histogram<T> {
  private final List<T> items;
  private final int[] sumCounts;

  public Histogram(List<T> items, int[] sumCounts) {
    this.items = Preconditions.checkNotNull(items);
    this.sumCounts = sumCounts;
    Preconditions.checkArgument(sumCounts.length == items.size());
    Preconditions.checkArgument(sumCounts.length > 0);
  }

  public T sample() {
    int sampleValue = Pseudorandom.get().nextInt(sumCounts[sumCounts.length - 1]);
    int index = Arrays.binarySearch(sumCounts, sampleValue);

    if (index >= 0) {
      return items.get(index + 1);
    } else {
      return items.get(-1 * (index + 1));
    }
  }

  public List<T> getItems() {
    return items;
  }
}
