package com.jayantkrish.jklol.tensor;

import java.util.Arrays;
import java.util.Iterator;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.util.ArrayUtils;
import com.jayantkrish.jklol.util.IntegerArrayIterator;

/**
 * Builder for incrementally constructing dense tensors.
 *
 * @author jayantk
 */
public class DenseTensorBuilder extends DenseTensorBase implements TensorBuilder {

  private static final long serialVersionUID = 1707937213062867772L;

  /**
   * Creates a {@code DenseTensorBuilder} with all values initialized to 0.
   * 
   * @param dimensions
   * @param sizes
   */
  public DenseTensorBuilder(int[] dimensions, int[] sizes) {
    super(dimensions, sizes);
    // Initialize the values of this builder to 0.
    Arrays.fill(values, 0.0);
  }

  /**
   * Creates a {@code DenseTensorBuilder} with all values initialized to
   * {@code initialValue}.
   * 
   * @param dimensions
   * @param sizes
   */
  public DenseTensorBuilder(int[] dimensions, int[] sizes, double initialValue) {
    super(dimensions, sizes);
    Arrays.fill(values, initialValue);
  }

  /**
   * Copy constructor
   * 
   * @param builder
   */
  public DenseTensorBuilder(DenseTensorBase builder) {
    super(builder.getDimensionNumbers(), builder.getDimensionSizes(),
        ArrayUtils.copyOf(builder.values, builder.values.length));
  }

  @Override
  public final void put(int[] key, double value) {
    values[dimKeyToIndex(key)] = value;
  }

  @Override
  public final void putByKeyNum(long keyNum, double value) {
    values[(int) keyNum] = value;
  }

  @Override
  public void increment(TensorBase other) {
    incrementWithMultiplier(other, 1.0);
  }

  @Override
  public void increment(double amount) {
    for (int i = 0; i < values.length; i++) {
      values[i] += amount;
    }
  }

  @Override
  public final void incrementEntry(double amount, int... key) {
    values[dimKeyToIndex(key)] += amount;
  }
  
  @Override
  public final void incrementEntryByKeyNum(double amount, long keyNum) {
    values[keyNumToIndex(keyNum)] += amount;
  }

  /**
   * {@inheritDoc}
   * 
   * This implementation supports increments when {@code other} has a subset of
   * {@code this}'s dimensions. In this case, the values in {@code other} are
   * implicitly replicated across all dimensions of {@code this} not present in
   * {@code other}.
   */
  @Override
  public void incrementWithMultiplier(TensorBase other, double multiplier) {
    if (Arrays.equals(other.getDimensionNumbers(), getDimensionNumbers())) {
      simpleIncrement(other, multiplier);
    } else {
      repmatIncrement(other, multiplier);
    }
  }

  /**
   * Increment algorithm for the case where both tensors have the same set of
   * dimensions.
   * 
   * @param other
   * @param multiplier
   */
  private void simpleIncrement(TensorBase other, double multiplier) {
    Preconditions.checkArgument(Arrays.equals(other.getDimensionNumbers(), getDimensionNumbers()));
    if (other instanceof DenseTensorBase) {
      double[] otherTensorValues = ((DenseTensorBase) other).values;
      Preconditions.checkArgument(otherTensorValues.length == values.length);
      int length = values.length;
      for (int i = 0; i < length; i++) {
        values[i] += otherTensorValues[i] * multiplier;
      }
    } else {
      int otherSize = other.size();
      for (int i = 0; i < otherSize; i++) {
        long keyNum = other.indexToKeyNum(i);
        double value = other.getByIndex(i);
        values[keyNumToIndex(keyNum)] += value * multiplier;
      }
    }
  }

  /**
   * Replicates the values in {@code tensor} across all dimensions of
   * {@code this}, incrementing each key in this appropriately. This function is
   * similar to summing two tensors after applying the matlab {@code repmat}
   * function.
   * 
   * @param other
   * @return
   */
  private void repmatIncrement(TensorBase other, double multiplier) {
    // Maps a key of other into a partial key of this.
    int[] dimensionMapping = getDimensionMapping(other.getDimensionNumbers());
    int[] partialKey = ArrayUtils.copyOf(getDimensionSizes(), getDimensionSizes().length);
    for (int i = 0; i < dimensionMapping.length; i++) {
      partialKey[dimensionMapping[i]] = 1;
    }

    int numValues = 1;
    for (int i = 0; i < partialKey.length; i++) {
      numValues *= partialKey[i];
    }
    int[] keyOffsets = new int[numValues];
    Iterator<int[]> myKeyIterator = new IntegerArrayIterator(partialKey, new int[0]);
    int ind = 0;
    while (myKeyIterator.hasNext()) {
      keyOffsets[ind] = dimKeyToIndex(myKeyIterator.next());
      ind++;
    }
    Preconditions.checkState(ind == keyOffsets.length);

    Iterator<KeyValue> otherKeyValues = other.keyValueIterator();
    while (otherKeyValues.hasNext()) {
      KeyValue otherKeyValue = otherKeyValues.next();
      int baseOffset = 0;
      for (int i = 0; i < otherKeyValue.getKey().length; i++) {
        baseOffset += otherKeyValue.getKey()[i] * indexOffsets[dimensionMapping[i]];
      }

      for (int i = 0; i < keyOffsets.length; i++) {
        values[baseOffset + keyOffsets[i]] += otherKeyValue.getValue() * multiplier;
      }
    }
  }

  @Override
  public void incrementOuterProductWithMultiplier(Tensor leftTensor, Tensor rightTensor,
      double multiplier) {
    int[] leftDimensionNums = leftTensor.getDimensionNumbers();
    int[] rightDimensionNums = rightTensor.getDimensionNumbers();
    Preconditions.checkArgument(leftDimensionNums[leftDimensionNums.length - 1] < rightDimensionNums[0]);
  
    long leftKeyNumMultiplier = rightTensor.getMaxKeyNum();
    int leftSize = leftTensor.size();
    int rightSize = rightTensor.size();
    long leftKeyNumOffset, rightKeyNum;
    double leftValue, rightValue;
    double[] leftValues = leftTensor.getValues();
    double[] rightValues = rightTensor.getValues();
    for (int i = 0; i < leftSize; i++) {
      leftKeyNumOffset = leftTensor.indexToKeyNum(i) * leftKeyNumMultiplier;
      leftValue = leftValues[i] * multiplier;
      for (int j = 0; j < rightSize; j++) {
        rightValue = rightValues[j];
        if (rightValue != 0.0) {
          rightKeyNum = rightTensor.indexToKeyNum(j);
          values[(int) (leftKeyNumOffset + rightKeyNum)] += leftValue * rightValue;
        }
      }
    }
  }

  @Override
  public void multiply(TensorBase other) {
    Preconditions.checkArgument(Arrays.equals(other.getDimensionNumbers(), getDimensionNumbers()));
    if (other instanceof DenseTensorBase) {
      DenseTensorBase otherTensor = (DenseTensorBase) other;
      Preconditions.checkArgument(otherTensor.values.length == values.length);
      for (int i = 0; i < values.length; i++) {
        values[i] *= otherTensor.values[i];
      }
    } else {
      Iterator<KeyValue> keyValueIter = keyValueIterator();
      while (keyValueIter.hasNext()) {
        KeyValue keyValue = keyValueIter.next();
        values[dimKeyToIndex(keyValue.getKey())] *= other.getByDimKey(keyValue.getKey());
      }
    }
  }

  @Override
  public void multiply(double amount) {
    for (int i = 0; i < values.length; i++) {
      values[i] *= amount;
    }
  }

  @Override
  public void multiplyEntry(double amount, int... key) {
    values[dimKeyToIndex(key)] *= amount;
  }
  
  @Override
  public final void multiplyEntryByKeyNum(double amount, long keyNum) {
    values[keyNumToIndex(keyNum)] *= amount;
  }

  @Override
  public void softThreshold(double threshold) {
    double negativeThreshold = -1.0 * threshold;
    for (int i = 0; i < values.length; i++) {
      if (values[i] > threshold) {
        values[i] -= threshold;
      } else if (values[i] < negativeThreshold) {
        values[i] += threshold;
      } else {
        values[i] = 0.0;
      }
    }
  }

  /**
   * Sets each {@code key} in {@code this} to the elementwise maximum of
   * {@code this[key]} and {@code other[key]}.
   * 
   * @param other
   */
  public void maximum(TensorBase other) {
    Preconditions.checkArgument(Arrays.equals(other.getDimensionNumbers(), getDimensionNumbers()));
    if (other instanceof DenseTensorBase) {
      DenseTensorBase otherTensor = (DenseTensorBase) other;
      Preconditions.checkArgument(otherTensor.values.length == values.length);
      for (int i = 0; i < values.length; i++) {
        values[i] = Math.max(otherTensor.values[i], values[i]);
      }
    } else {
      Iterator<KeyValue> keyValueIter = keyValueIterator();
      while (keyValueIter.hasNext()) {
        KeyValue keyValue = keyValueIter.next();
        int index = dimKeyToIndex(keyValue.getKey()); 
        values[index] = Math.max(values[index], other.getByDimKey(keyValue.getKey()));
      }
    }
  }
      
  @Override
  public double innerProduct(TensorBase other) {
    Preconditions.checkArgument(Arrays.equals(other.getDimensionNumbers(), getDimensionNumbers()));
    if (other instanceof DenseTensorBase) {
      DenseTensorBase otherTensor = (DenseTensorBase) other;
      Preconditions.checkArgument(otherTensor.values.length == values.length);
      double total = 0.0;
      for (int i = 0; i < values.length; i++) {
        total += otherTensor.values[i] * values[i];
      }
      return total;
    } else {
      double total = 0.0;
      
      Iterator<KeyValue> keyValueIter = keyValueIterator();
      while (keyValueIter.hasNext()) {
        KeyValue keyValue = keyValueIter.next();
        int index = dimKeyToIndex(keyValue.getKey());
        
        total += values[index] * other.getByDimKey(keyValue.getKey());
      }
      return total;
    }
  }

  @Override
  public void exp() {
    for (int i = 0; i < values.length; i++) {
      values[i] = Math.exp(values[i]);
    }
  }

  @Override
  public DenseTensor build() {
    return new DenseTensor(getDimensionNumbers(), getDimensionSizes(), ArrayUtils.copyOf(values, values.length));
  }

  /**
   * Faster version of {@code build()} that does not copy the values into a new
   * array. Use this method instead of {@code build()} when {@code this} is not
   * modified after the call.
   * 
   * @return
   */
  @Override
  public DenseTensor buildNoCopy() {
    return new DenseTensor(getDimensionNumbers(), getDimensionSizes(), values);
  }

  @Override
  public DenseTensorBuilder getCopy() {
    return new DenseTensorBuilder(this);
  }

  @Override
  public String toString() {
    return Arrays.toString(values);
  }

  // ///////////////////////////////////////////////////////////////////
  // Static Methods
  // ///////////////////////////////////////////////////////////////////

  /**
   * Gets a {@code TensorFactory} which creates {@code DenseTensorBuilder}s.
   * 
   * @return
   */
  public static TensorFactory getFactory() {
    return new TensorFactory() {
      @Override
      public TensorBuilder getBuilder(int[] dimNums, int[] dimSizes) {
        return new DenseTensorBuilder(dimNums, dimSizes);
      }
    };
  }

  /**
   * Gets a builder which contains the same key value pairs as {@code tensor}.
   * 
   * @param tensor
   * @return
   */
  public static DenseTensorBuilder copyOf(TensorBase tensor) {
    DenseTensorBuilder builder = new DenseTensorBuilder(tensor.getDimensionNumbers(),
        tensor.getDimensionSizes());
    Iterator<KeyValue> initialWeightIter = tensor.keyValueIterator();
    while (initialWeightIter.hasNext()) {
      KeyValue keyValue = initialWeightIter.next();
      builder.put(keyValue.getKey(), keyValue.getValue());
    }
    return builder;
  }
}