package com.jayantkrish.jklol.util;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.tensor.Tensor;

public class Histogram<T> {
  private final List<T> items;
  private final double[] doubleCounts;
  private final int[] sumCounts;

  public Histogram(List<T> items, int[] sumCounts) {
    this.items = Preconditions.checkNotNull(items);
    this.sumCounts = sumCounts;
    Preconditions.checkArgument(sumCounts.length == items.size());
    Preconditions.checkArgument(sumCounts.length > 0);
    
    // This is a hack to get the tensor-based conditional sampling
    // to work efficiently.
    doubleCounts = new double[sumCounts.length];
    doubleCounts[0] = sumCounts[0];
    for (int i = 1; i < sumCounts.length; i++) {
      doubleCounts[i] = sumCounts[i] - sumCounts[i - 1];
    }
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
 
  public T sampleConditional(Tensor usableIndexes) {
    Tensor countTensor = new DenseTensor(new int[] {0}, new int[] {items.size()}, doubleCounts);
    Tensor conditionalCounts = usableIndexes.elementwiseProduct(countTensor);

    double totalValue = conditionalCounts.sumOutDimensions(new int[] {0}).getByDimKey(new int[] {});
    
    double draw = Pseudorandom.get().nextDouble() * totalValue;
    
    double[] conditionalCountValues = conditionalCounts.getValues();
    double currentSum = 0;
    for (int i = 0; i < conditionalCountValues.length; i++) {
      currentSum += conditionalCountValues[i];
      if (draw < currentSum) {
        return items.get((int) conditionalCounts.indexToKeyNum(i));
      }
    }

    // This state shouldn't ever happen, but there may be floating
    // point issues. If so, the check should be removed (and the
    // return statement is correct).
    Preconditions.checkState(false, "Sampling failed. draw: %s, currentSum %s, totalValues %s",
        draw, currentSum, totalValue);
    return items.get((int) conditionalCounts.indexToKeyNum(conditionalCountValues.length - 1));
  }

  public List<T> getItems() {
    return items;
  }
}
