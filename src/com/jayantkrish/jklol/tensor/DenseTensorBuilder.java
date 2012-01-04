package com.jayantkrish.jklol.tensor;

import java.util.Arrays;
import java.util.Iterator;

import com.google.common.base.Preconditions;

public class DenseTensorBuilder extends DenseTensorBase implements TensorBuilder {

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

  @Override
  public void put(int[] key, double value) {
    values[getIndex(key)] = value;
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
  public void incrementEntry(double amount, int... key) {
    values[getIndex(key)] += amount;
  }

  @Override
  public void incrementWithMultiplier(TensorBase other, double multiplier) {
    Preconditions.checkArgument(Arrays.equals(other.getDimensionNumbers(), getDimensionNumbers()));
    if (other instanceof DenseTensorBase) {
      DenseTensorBase otherTensor = (DenseTensorBase) other;
      Preconditions.checkArgument(otherTensor.values.length == values.length);
      for (int i = 0; i < values.length; i++) {
        values[i] += otherTensor.values[i] * multiplier;
      }
    } else {
      Iterator<int[]> otherKeyIterator = other.keyIterator();
      while (otherKeyIterator.hasNext()) {
        int[] otherKey = otherKeyIterator.next();
        values[getIndex(otherKey)] += other.get(otherKey) * multiplier; 
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
      Iterator<int[]> keyIter = keyIterator();
      while (keyIter.hasNext()) {
        int[] key = keyIter.next();
        values[getIndex(key)] *= other.get(key);
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
    values[getIndex(key)] *= amount;
  }

  @Override
  public Tensor build() {
    return new DenseTensor(getDimensionNumbers(), getDimensionSizes(), Arrays.copyOf(values, values.length));
  }

  /**
   * Faster version of {@code build()} that does not copy the values into a new
   * array. Use this method instead of {@code build()} when {@code this} is not
   * modified after the call.
   * 
   * @return
   */
  public Tensor buildNoCopy() {
    return new DenseTensor(getDimensionNumbers(), getDimensionSizes(), values);
  }

  @Override
  public String toString() {
    return Arrays.toString(values);
  }
}
