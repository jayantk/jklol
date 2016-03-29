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
    // info.yeppp.Core.Add_IV64fS64f_IV64f(values, 0, amount, values.length);
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
    values[(int) keyNum] += amount;
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

  @Override
  public void incrementSquare(TensorBase other, double multiplier) {
    if (other instanceof DenseTensorBase) {
      // TODO: optimize
      double square = multiplier * multiplier;
      double[] otherTensorValues = ((DenseTensorBase) other).values;
      Preconditions.checkArgument(otherTensorValues.length == values.length);

      /*
      double[] squaredOtherValues = Arrays.copyOf(otherTensorValues, otherTensorValues.length);
      info.yeppp.Core.Multiply_IV64fV64f_IV64f(squaredOtherValues, 0, squaredOtherValues, 0, squaredOtherValues.length);
      info.yeppp.Core.Multiply_IV64fS64f_IV64f(squaredOtherValues, 0, square, squaredOtherValues.length);
      info.yeppp.Core.Add_IV64fV64f_IV64f(values, 0, squaredOtherValues, 0, squaredOtherValues.length);
      */

      int length = values.length;
      double otherVal = 0;
      for (int i = 0; i < length; i++) {
        otherVal = otherTensorValues[i];
        values[i] += otherVal * otherVal * square;
      }
    } else {
      throw new UnsupportedOperationException();
    }
  }

  @Override
  public void incrementAdagrad(TensorBase other, TensorBase squareTensor, double multiplier) {
    if (other instanceof DenseTensorBase && squareTensor instanceof DenseTensorBase) {
      double[] otherTensorValues = ((DenseTensorBase) other).values;
      double[] squareTensorValues = ((DenseTensorBase) squareTensor).values;
      Preconditions.checkArgument(otherTensorValues.length == values.length);
      Preconditions.checkArgument(squareTensorValues.length == values.length);
      int length = values.length;
      double otherVal = 0;
      double squareVal = 0;
      for (int i = 0; i < length; i++) {
        otherVal = otherTensorValues[i];
        squareVal = squareTensorValues[i];
        if (squareVal != 0.0) {
          values[i] += otherVal * multiplier / Math.sqrt(squareVal);
        }
      }
    } else {
      throw new UnsupportedOperationException();
    }
  }

  @Override
  public void multiplyInverseAdagrad(TensorBase squareTensor, double constant, double multiplier) {
    if (squareTensor instanceof DenseTensorBase) {
      double[] squareTensorValues = ((DenseTensorBase) squareTensor).values;
      Preconditions.checkArgument(squareTensorValues.length == values.length);
      int length = values.length;
      double squareVal = 0;
      for (int i = 0; i < length; i++) {
        squareVal = squareTensorValues[i];
        if (squareVal != 0.0) {
          squareVal = 1 / squareVal;
        }
        values[i] *= (constant + (multiplier * Math.sqrt(squareVal)));
      }
    } else {
      throw new UnsupportedOperationException();
    }
  }
  
  @Override
  public void incrementSquareAdagrad(TensorBase gradient, TensorBase parameters, double multiplier) {
    if (gradient instanceof DenseTensorBase && parameters instanceof DenseTensorBase) {
      double[] gradientTensorValues = ((DenseTensorBase) gradient).values;
      Preconditions.checkArgument(gradientTensorValues.length == values.length);
      
      double[] parameterTensorValues = ((DenseTensorBase) parameters).values;
      Preconditions.checkArgument(parameterTensorValues.length == values.length);
      
      int length = values.length;
      double val = 0;
      for (int i = 0; i < length; i++) {
        val = gradientTensorValues[i] + (multiplier * parameterTensorValues[i]);
        values[i] += val * val;
      }
    } else {
      throw new UnsupportedOperationException();
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
    if (leftDimensionNums.length == 0) {
      incrementWithMultiplier(rightTensor, multiplier * leftTensor.getByDimKey());
      return;
    } else if (rightDimensionNums.length == 0) {
      incrementWithMultiplier(leftTensor, multiplier * rightTensor.getByDimKey());
      return;
    }

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
  public void incrementInnerProductWithMultiplier(Tensor leftTensor, Tensor rightTensor,
      double multiplier) {
    int[] leftDimensionNums = leftTensor.getDimensionNumbers();
    int[] rightDimensionNums = rightTensor.getDimensionNumbers();
    if (leftDimensionNums.length == 0) {
      incrementWithMultiplier(rightTensor, multiplier * leftTensor.getByDimKey());
      return;
    } else if (rightDimensionNums.length == 0) {
      incrementWithMultiplier(leftTensor, multiplier * rightTensor.getByDimKey());
      return;
    }

    // rightDimensionNums must be a left-aligned subset of leftDimensionNums.
    int[] leftToRightAlignedDims = Arrays.copyOfRange(leftDimensionNums, 0, rightDimensionNums.length);
    Preconditions.checkArgument(Arrays.equals(leftToRightAlignedDims, rightDimensionNums),
        "Invalid dimension alignment.");
    
    // The remaining dimension nums must be equal to the dimension nums of this
    // TensorBuilder
    int[] leftToBuilderAlignedDims = Arrays.copyOfRange(leftDimensionNums,
        leftToRightAlignedDims.length, leftDimensionNums.length);
    Preconditions.checkArgument(Arrays.equals(leftToBuilderAlignedDims, getDimensionNumbers()),
        "Invalid dimension alignment");
    
    long keyNumSplit = getMaxKeyNum(); 

    int leftSize = leftTensor.size();
    double[] leftValues = leftTensor.getValues();
    long leftKeyNum = -1;
    long myKeyNum = -1;
    long rightKeyNum = -1;
    double oldRightKeyNum = -1;
    double rightValue = -1;
    for (int i = 0; i < leftSize; i++) {
      leftKeyNum = leftTensor.indexToKeyNum(i);
      myKeyNum = leftKeyNum % keyNumSplit;
      rightKeyNum = leftKeyNum / keyNumSplit;

      if (rightKeyNum != oldRightKeyNum) {
        rightValue = rightTensor.get(rightKeyNum) * multiplier;
        oldRightKeyNum = rightKeyNum;
      }

      values[(int) myKeyNum] += leftValues[i] * rightValue;
    }
  }

  @Override
  public void multiply(TensorBase other) {
    Preconditions.checkArgument(Arrays.equals(other.getDimensionNumbers(), getDimensionNumbers()));
    if (other instanceof DenseTensorBase) {
      DenseTensorBase otherTensor = (DenseTensorBase) other;
      Preconditions.checkArgument(otherTensor.values.length == values.length);

      // info.yeppp.Core.Multiply_V64fV64f_V64f(values, 0, otherTensor.values, 0, values, 0, values.length);

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
    // Not optimized.
    // info.yeppp.Core.Multiply_V64fS64f_V64f(values, 0, amount, values, 0, values.length);
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
  
  @Override
  public void findEntriesLargerThan(double threshold) {
    for (int i = 0; i < values.length; i++) {
      if (values[i] >= threshold) {
        values[i] = 1.0;
      } else {
        values[i] = 0.0;
      }
    }
  }

  @Override
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