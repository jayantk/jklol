package com.jayantkrish.jklol.tensor;

import java.util.Arrays;

/**
 * Mapping from keys of one tensor to keys of a second tensor. Such mappings
 * frequently represent how keys of one tensor were derived from a second
 * tensor.
 * 
 * @author jayantk
 */
public class Backpointers {

  private long[] oldKeyNums;
  private long[] newKeyNums;
  
  private Tensor oldTensor;

  public void setBackpointers(long[] newKeyNums, long[] oldKeyNums, int size,
      Tensor oldTensor) {
    this.newKeyNums = Arrays.copyOf(newKeyNums, size);
    this.oldKeyNums = Arrays.copyOf(oldKeyNums, size);
    this.oldTensor = oldTensor;
  }

  /**
   * Gets the old key associated with {@code newKeyNum} in this.
   * 
   * @param newKeyNum
   * @return
   */
  public long getBackpointer(long newKeyNum) {
    int index = Arrays.binarySearch(newKeyNums, newKeyNum);
    if (index >= 0) {
      return oldKeyNums[index];
    }
    return -1;
  }

  /**
   * Gets a tensor of indicator variables for the old key values in this. The
   * returned tensor has value 1 for all keys in this, and 0 for all other keys.
   * 
   * @return
   */
  public SparseTensor getOldKeyIndicatorTensor() {
    double[] values = new double[oldKeyNums.length];
    Arrays.fill(values, 1.0);
    return SparseTensor.fromUnorderedKeyValues(oldTensor.getDimensionNumbers(), 
        oldTensor.getDimensionSizes(), oldKeyNums, values);
  }

  @Override
  public String toString() {
    return Arrays.toString(newKeyNums) + " " + Arrays.toString(oldKeyNums);
  }
}
