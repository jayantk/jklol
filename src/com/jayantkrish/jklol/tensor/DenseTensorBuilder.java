package com.jayantkrish.jklol.tensor;

import java.util.Arrays;

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
  
  @Override
  public void put(int[] key, double value) {
    values[getIndex(key)] = value;
  }
  
  @Override
  public void increment(TensorBase other) {
    Preconditions.checkArgument(Arrays.equals(other.getDimensionNumbers(), getDimensionNumbers()));
    if (other instanceof DenseTensorBase) {
      DenseTensorBase otherTensor = (DenseTensorBase) other;
      Preconditions.checkArgument(otherTensor.values.length == values.length);
      for (int i = 0; i < values.length; i++) {
        values[i] += otherTensor.values[i];
      }
    } else {
      throw new UnsupportedOperationException("Not yet implemented.");
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
      throw new UnsupportedOperationException("Not yet implemented.");
    }
  }

  @Override
  public Tensor build() {
    throw new UnsupportedOperationException("Not yet implemented");
  }
}
