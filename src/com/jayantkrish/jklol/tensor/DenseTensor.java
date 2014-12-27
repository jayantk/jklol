package com.jayantkrish.jklol.tensor;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.SortedSet;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.util.ArrayUtils;
import com.jayantkrish.jklol.util.Pseudorandom;

/**
 * Immutable tensor, represented densely. The dense representation is faster
 * than {@link SparseTensor}, but requires more memory.
 * 
 * @author jayantk
 */
public class DenseTensor extends DenseTensorBase implements Tensor, Serializable {

  private static final long serialVersionUID = 1L;

  /**
   * Creates a tensor that spans {@code dimensions}, and each dimension has the
   * corresponding size from {@code sizes}. Most users should use a
   * {@link DenseTensorBuilder} instead of this constructor.
   * 
   * @param dimensions
   * @param sizes
   * @param values
   */
  public DenseTensor(int[] dimensions, int[] sizes, double[] values) {
    super(dimensions, sizes, values);
  }

  @Override
  public int getNearestIndex(long keyNum) {
    // Dense tensors contain values for all keyNums.
    return (int) keyNum;
  }

  @Override
  public double[] getValues() {
    return super.values;
  }

  @Override
  public DenseTensor slice(int[] dimensionNumbers, int[] key) {
    if (dimensionNumbers.length == 0) {
      return this;
    }

    int[] myDimensionNumbers = getDimensionNumbers();
    int[] myDimensionSizes = getDimensionSizes();
    long[] myDimensionOffsets = getDimensionOffsets();
    if (ArrayUtils.subarrayEquals(myDimensionNumbers, dimensionNumbers, 0)) {
      int firstKeyNum = (int) dimKeyPrefixToKeyNum(key);
      int lastKeyNum = firstKeyNum + (int) myDimensionOffsets[dimensionNumbers.length - 1];
      double[] newValues = new double[lastKeyNum - firstKeyNum];
      for (int i = firstKeyNum; i < lastKeyNum; i++) {
        newValues[i - firstKeyNum] = values[i];
      }

      int[] newDimensions = ArrayUtils.copyOfRange(myDimensionNumbers, 
          dimensionNumbers.length, myDimensionNumbers.length);
      int[] newSizes = ArrayUtils.copyOfRange(myDimensionSizes,
          dimensionNumbers.length, myDimensionNumbers.length);
      return new DenseTensor(newDimensions, newSizes, newValues);
    }

    // TODO(jayantk): This is an extremely naive implementation of slice.

    // Figure out the appropriate sizes for the subset of dimensions.
    int[] dimensionSizes = new int[dimensionNumbers.length];
    for (int i = 0; i < dimensionNumbers.length; i++) {
      int dimIndex = getDimensionIndex(dimensionNumbers[i]);
      Preconditions.checkArgument(dimIndex >= 0);
      dimensionSizes[i] = getDimensionSizes()[dimIndex];
    }
    SparseTensorBuilder builder = new SparseTensorBuilder(dimensionNumbers, dimensionSizes);
    builder.put(key, 1.0);
    return elementwiseProduct(builder.build()).sumOutDimensions(Ints.asList(dimensionNumbers));
  }
  
  @Override
  public DenseTensor retainKeys(Tensor indicatorTensor) {
    return this;
  }
  
  @Override
  public Tensor findKeysLargerThan(double thresholdValue) {
    Preconditions.checkArgument(thresholdValue > 0.0);
    long[] resultKeyNums = new long[values.length];
    
    int resultInd = 0;    
    int numKeys = values.length;
    for (int i = 0; i < numKeys; i++) {
      if (values[i] > thresholdValue) {
        resultKeyNums[resultInd] = i;
        resultInd++;
      }
    }
    
    long[] resizedKeyNums = ArrayUtils.copyOfRange(resultKeyNums, 0, resultInd);
    double[] resizedValues = new double[resultInd];
    Arrays.fill(resizedValues, 1.0);
    
    return new SparseTensor(getDimensionNumbers(), getDimensionSizes(), 
        resizedKeyNums, resizedValues);
  }

  @Override
  public DenseTensor elementwiseProduct(Tensor other) {
    DenseTensorBuilder result = new DenseTensorBuilder(getDimensionNumbers(),
        getDimensionSizes());
    result.incrementWithMultiplier(other, 1);
    result.multiply(this);
    return result.buildNoCopy();
  }

  @Override
  public DenseTensor elementwiseProduct(Collection<Tensor> others) {
    DenseTensor result = this;
    for (Tensor other : others) {
      result = result.elementwiseProduct(other);
    }
    return result;
  }

  @Override
  public DenseTensor elementwiseProduct(double constant) {
    /*
    double[] newValues = Arrays.copyOf(values, values.length);
    for (int i = 0; i < values.length; i++) {
      newValues[i] *= constant;
    }
    */

    double[] newValues = new double[values.length];
    info.yeppp.Core.Multiply_V64fS64f_V64f(values, 0, constant, newValues, 0, values.length);

    return new DenseTensor(getDimensionNumbers(), getDimensionSizes(), newValues);
  }

  @Override
  public DenseTensor innerProduct(Tensor other) {
    int[] otherDims = other.getDimensionNumbers();
    int[] otherSizes = other.getDimensionSizes();
    if (otherDims.length == 0) {
      // Both products coincide in this case. 
      return elementwiseProduct(other);
    }

    int[] myDims = getDimensionNumbers();
    int[] mySizes = getDimensionSizes();
    // If the other tensor has the same dimensionality as this tensor, 
    // and is dense, the inner product is a simple array operation.
    if (Arrays.equals(otherDims, myDims) && other instanceof DenseTensor) {
      return DenseTensor.scalar(denseTensorInnerProduct((DenseTensor) other));
    }

    // Check if the dimensions of other are either left- or right-aligned 
    // with this tensor's dimensions, in which case we can use a faster
    // inner product algorithm.
    Preconditions.checkArgument(otherDims.length <= myDims.length);
    if (areDimensionsRightAligned(otherDims)) {
      int maxDimIndex = myDims.length - (otherDims.length + 1);
      int[] newDims = ArrayUtils.copyOf(myDims, maxDimIndex + 1);
      int[] newSizes = ArrayUtils.copyOf(getDimensionSizes(), maxDimIndex + 1);
      long maxKeyNum = getMaxKeyNum();
      long keyNumIncrement = (maxDimIndex < 0) ? maxKeyNum : getDimensionOffsets()[maxDimIndex];
      
      for (int i = 1; i < otherSizes.length; i++) {
        Preconditions.checkArgument(mySizes[mySizes.length - i] == otherSizes[otherSizes.length - i],
            "Tensor dimension sizes do not agree: %s and %s", Ints.asList(mySizes), 
            Ints.asList(otherSizes));
      }

      return fastInnerProductRightAligned(other, maxKeyNum, keyNumIncrement, newDims, newSizes);
    } else if (areDimensionsLeftAligned(otherDims)) {
      int minDimIndex = otherDims.length;
      int[] newDims = ArrayUtils.copyOfRange(myDims, minDimIndex, myDims.length);
      int[] newSizes = ArrayUtils.copyOfRange(getDimensionSizes(), minDimIndex, myDims.length);
      long maxKeyNum = (minDimIndex == 0) ? getMaxKeyNum() : getDimensionOffsets()[minDimIndex - 1];

      for (int i = 0; i < minDimIndex; i++) {
        Preconditions.checkArgument(mySizes[i] == otherSizes[i], 
            "Tensor dimension sizes do not agree: %s and %s", Ints.asList(mySizes),
            Ints.asList(otherSizes));
      }

      return fastInnerProductLeftAligned(other, maxKeyNum, newDims, newSizes);
    } else {
      // Slow, default inner product.
      return elementwiseProduct(other).sumOutDimensions(otherDims);
    }
  }
  
  @Override
  public Tensor matrixInnerProduct(Tensor other) {
    return AbstractTensor.innerProduct(this, other, DenseTensorBuilder.getFactory());
  }
  
  /**
   * Implementation of inner product where both tensors are dense and have
   * the same dimensionality. These properties enable the inner product to
   * be computed extremely quickly by iterating over both dense arrays of
   * values.
   *  
   * @param other
   * @return
   */
  private double denseTensorInnerProduct(DenseTensor other) {
    double[] otherValues = other.values;
    int length = values.length;
    Preconditions.checkArgument(otherValues.length == length);
    
    return info.yeppp.Core.DotProduct_V64fV64f_S64f(values, 0, otherValues, 0, length);
    /*
    double innerProduct = 0.0;
    for (int i = 0; i < length; i++) {
      innerProduct += values[i] * otherValues[i];
    }
    return innerProduct;
    */
  }

  /**
   * Fast implementation of inner product that takes advantage of potential 
   * sparsity in {@code other}. Requires alignment between the dimensions of 
   * {@code this} and {@code other}. 
   * 
   * @param other
   * @return
   */
  private DenseTensor fastInnerProductRightAligned(Tensor other, long maxKeyNum, long keyNumIncrement, 
      int[] newDims, int[] newSizes) {        
    DenseTensorBuilder resultBuilder = new DenseTensorBuilder(newDims, newSizes);
    int otherSize = other.size();
    double[] otherValues = other.getValues();
    // Iterate over the keys of this, then (hopefully sparsely) iterate over the 
    // keys of {@code other},
    double innerProd;
    int otherIndex;
    int finalIndex = (int) (maxKeyNum / keyNumIncrement);
    long myKeyNum;
    for (int i = 0; i < finalIndex; i++) {
      myKeyNum = i * keyNumIncrement;
      innerProd = 0.0;
      for (otherIndex = 0; otherIndex < otherSize; otherIndex++) {
        long otherKeyNum = other.indexToKeyNum(otherIndex);
        double otherValue = otherValues[otherIndex];
        innerProd += values[(int) (myKeyNum + otherKeyNum)] * otherValue;
      }
      resultBuilder.putByKeyNum(i, innerProd);
    }
    return resultBuilder.buildNoCopy();
  }

  private DenseTensor fastInnerProductLeftAligned(Tensor other, long maxKeyNum,
      int[] newDims, int[] newSizes) {
    DenseTensorBuilder resultBuilder = new DenseTensorBuilder(newDims, newSizes);
    int otherSize = other.size();
    double[] otherValues = other.getValues();
    // Iterate over the keys of this tensor in the inner loop for
    // better cache locality.
    double otherValue;
    int finalIndex = (int) maxKeyNum;
    int otherKeyNum;
    for (int otherIndex = 0; otherIndex < otherSize; otherIndex++) {
      otherKeyNum = (int) (other.indexToKeyNum(otherIndex) * maxKeyNum);
      otherValue = otherValues[otherIndex];
      for (int i = 0; i < finalIndex; i++) {
        resultBuilder.incrementEntryByKeyNum(values[i + otherKeyNum] * otherValue, i);
      }
    }
    return resultBuilder.buildNoCopy();
  }

  @Override
  public DenseTensor outerProduct(Tensor other) {
    int[] otherDims = other.getDimensionNumbers();
    int[] myDims = getDimensionNumbers();
    
    if (otherDims.length == 0) {
      // Both tensor products coincide when the other tensor has no dimensions (is a scalar).
      return elementwiseProduct(other);
    } 

    if (myDims.length == 0 || myDims[myDims.length - 1] < otherDims[0]) {
      // Fast implementation for when all dimensions of this tensor 
      // are smaller than the dimensions of the other tensor.
      int[] newDims = Ints.concat(myDims, otherDims);
      int[] newSizes = Ints.concat(getDimensionSizes(), other.getDimensionSizes());
      DenseTensorBuilder builder = new DenseTensorBuilder(newDims, newSizes);
      builder.increment(this);
      
      return builder.buildNoCopy().elementwiseProduct(other);
    } else {
      return DenseTensor.copyOf(AbstractTensor.outerProduct(this, other));
    }
  }

  @Override
  public DenseTensor elementwiseAddition(Tensor other) {
    DenseTensorBuilder result = new DenseTensorBuilder(getDimensionNumbers(),
        getDimensionSizes());
    result.incrementWithMultiplier(other, 1);
    result.increment(this);
    return result.buildNoCopy();
  }
  
  @Override
  public DenseTensor elementwiseAddition(double value) {
    DenseTensorBuilder result = new DenseTensorBuilder(getDimensionNumbers(),
        getDimensionSizes());
    result.increment(this);
    result.increment(value);
    return result.buildNoCopy();
  }

  @Override
  public DenseTensor elementwiseMaximum(Tensor other) {
    DenseTensorBuilder result = new DenseTensorBuilder(getDimensionNumbers(),
        getDimensionSizes());
    result.incrementWithMultiplier(other, 1);
    result.maximum(this);
    return result.buildNoCopy();
  }

  @Override
  public DenseTensor elementwiseInverse() {
    DenseTensorBuilder outputBuilder = new DenseTensorBuilder(getDimensionNumbers(),
        getDimensionSizes());
    for (int i = 0; i < values.length; i++) {
      outputBuilder.values[i] = (values[i] == 0.0) ? 0 : 1.0 / values[i];
    }
    return outputBuilder.buildNoCopy();
  }
  
  @Override
  public DenseTensor elementwiseSqrt() {
    DenseTensorBuilder outputBuilder = new DenseTensorBuilder(getDimensionNumbers(),
        getDimensionSizes());
    for (int i = 0; i < values.length; i++) {
      outputBuilder.values[i] = Math.sqrt(values[i]);
    }

    return outputBuilder.buildNoCopy();
  }

  @Override
  public DenseTensor elementwiseLog() {
    DenseTensorBuilder outputBuilder = new DenseTensorBuilder(getDimensionNumbers(),
        getDimensionSizes());
    info.yeppp.Math.Log_V64f_V64f(values, 0, outputBuilder.values, 0, values.length);
    /*
    for (int i = 0; i < values.length; i++) {
      outputBuilder.values[i] = Math.log(values[i]);
    }
    */
    return outputBuilder.buildNoCopy();
  }

  @Override
  public DenseTensor elementwiseExp() {
    DenseTensorBuilder outputBuilder = new DenseTensorBuilder(getDimensionNumbers(),
        getDimensionSizes());
    info.yeppp.Math.Exp_V64f_V64f(values, 0, outputBuilder.values, 0, values.length);
    /*
    for (int i = 0; i < values.length; i++) {
      outputBuilder.values[i] = Math.exp(values[i]);
    }
    */
    return outputBuilder.buildNoCopy();
  }

  @Override
  public DenseTensor elementwiseTanh() {
    DenseTensorBuilder outputBuilder = new DenseTensorBuilder(getDimensionNumbers(),
        getDimensionSizes());
    for (int i = 0; i < values.length; i++) {
      outputBuilder.values[i] = Math.tanh(values[i]);
    }
    return outputBuilder.buildNoCopy();
  }
  
  @Override
  public DenseTensor elementwiseAbs() {
    DenseTensorBuilder outputBuilder = new DenseTensorBuilder(getDimensionNumbers(),
        getDimensionSizes());
    for (int i = 0; i < values.length; i++) {
      outputBuilder.values[i] = Math.abs(values[i]);
    }
    return outputBuilder.buildNoCopy();
  }

  @Override
  public DenseTensor elementwiseLaplaceSigmoid(double smoothness) {
    DenseTensorBuilder outputBuilder = new DenseTensorBuilder(getDimensionNumbers(),
        getDimensionSizes());
    for (int i = 0; i < values.length; i++) {
      double value = values[i];
      if (value > 0) {
        outputBuilder.values[i] = 1 - Math.exp(-1 * smoothness * value);
      } else if (value < 0) {
        outputBuilder.values[i] = -1 + Math.exp(smoothness * value);
      } else {
        outputBuilder.values[i] = 0;
      }
    }
    return outputBuilder.buildNoCopy();
  }

  @Override
  public DenseTensor softThreshold(double threshold) {
    DenseTensorBuilder builder = DenseTensorBuilder.copyOf(this);
    builder.softThreshold(threshold);
    return builder.build();
  }

  @Override
  public DenseTensor getEntriesLargerThan(double threshold) {
    DenseTensorBuilder builder = DenseTensorBuilder.copyOf(this);
    builder.findEntriesLargerThan(threshold);
    return builder.build();
  }

  @Override
  public DenseTensor sumOutDimensions(Collection<Integer> dimensionsToEliminate) {
    return reduceDimensions(dimensionsToEliminate, true, null);
  }
   
  @Override
  public DenseTensor sumOutDimensions(int... dimensionsToEliminate) {
    return sumOutDimensions(Ints.asList(dimensionsToEliminate));
  }
  
  @Override
  public Tensor logSumOutDimensions(Collection<Integer> dimensionsToEliminate) {
    return AbstractTensor.logSumOutDimensions(this, dimensionsToEliminate);
  }

  @Override 
  public Tensor logSumOutDimensions(int[] dimensionsToEliminate) {
    return AbstractTensor.logSumOutDimensions(this, Ints.asList(dimensionsToEliminate));
  }


  @Override
  public DenseTensor maxOutDimensions(Collection<Integer> dimensionsToEliminate) {
    return reduceDimensions(dimensionsToEliminate, false, null);
  }
  
  @Override
  public DenseTensor maxOutDimensions(int[] dimensionsToEliminate) {
    return maxOutDimensions(Ints.asList(dimensionsToEliminate));
  }

  @Override
  public DenseTensor maxOutDimensions(Collection<Integer> dimensionsToEliminate,
      Backpointers backpointers) {
    return reduceDimensions(dimensionsToEliminate, false, backpointers);
  }
  
  @Override
  public DenseTensor maxOutDimensions(int[] dimensionsToEliminate, Backpointers backpointers) {
    return maxOutDimensions(Ints.asList(dimensionsToEliminate), backpointers);
  }

  /**
   * Performs reduction operations which eliminate some subset of the existing
   * dimensions.
   * 
   * @param dimensionsToEliminate
   * @param useSum
   * @return
   */
  private DenseTensor reduceDimensions(Collection<Integer> dimensionsToEliminate,
      boolean useSum, Backpointers backpointers) {
    SortedSet<Integer> dimensionsToKeep = Sets.newTreeSet(Ints.asList(getDimensionNumbers()));
    dimensionsToKeep.removeAll(dimensionsToEliminate);

    int[] myDimensionNumbers = getDimensionNumbers();
    int[] myDimensionSizes = getDimensionSizes();
    List<Integer> dimensionNumsToKeep = Lists.newArrayList();
    List<Integer> dimensionSizesToKeep = Lists.newArrayList();
    List<Integer> dimensionNumsToEliminate = Lists.newArrayList();
    List<Integer> dimensionSizesToEliminate = Lists.newArrayList();
    for (int i = 0; i < myDimensionNumbers.length; i++) {
      if (!dimensionsToEliminate.contains(myDimensionNumbers[i])) {
        dimensionNumsToKeep.add(myDimensionNumbers[i]);
        dimensionSizesToKeep.add(myDimensionSizes[i]);
      } else {
        dimensionNumsToEliminate.add(myDimensionNumbers[i]);
        dimensionSizesToEliminate.add(myDimensionSizes[i]);
      }
    }

    DenseTensorBuilder outputBuilder = new DenseTensorBuilder(Ints.toArray(dimensionNumsToKeep),
        Ints.toArray(dimensionSizesToKeep));
    // Optionally return the list of keynums which determined the values
    // in the returned tensor.
    long[] newBackpointerNums = null;
    long[] oldBackpointerNums = null;
    if (backpointers != null) {
      newBackpointerNums = new long[outputBuilder.values.length];
      oldBackpointerNums = new long[outputBuilder.values.length];
    }

    // Maps a key of the reduced tensor into a partial key of this.
    int[] dimensionMapping = getDimensionMapping(outputBuilder.getDimensionNumbers());
    int[] partialKey = new int[getDimensionNumbers().length];
    Arrays.fill(partialKey, -1);
    Iterator<KeyValue> otherKeyValues = outputBuilder.keyValueIterator();

    int backpointerIndex = 0;
    while (otherKeyValues.hasNext()) {
      KeyValue otherKeyValue = otherKeyValues.next();
      for (int i = 0; i < otherKeyValue.getKey().length; i++) {
        partialKey[dimensionMapping[i]] = otherKeyValue.getKey()[i];
      }

      Iterator<int[]> myKeyIterator = new SliceIndexIterator(getDimensionSizes(), partialKey);
      double resultVal = (useSum) ? 0.0 : Double.NEGATIVE_INFINITY;
      long backpointerKeyNum = -1;
      while (myKeyIterator.hasNext()) {
        if (useSum) {
          resultVal += getByDimKey(myKeyIterator.next());
        } else {
          int[] nextKey = myKeyIterator.next();
          double keyVal = getByDimKey(nextKey);
          if (keyVal > resultVal) {
            resultVal = keyVal;
            backpointerKeyNum = dimKeyToKeyNum(nextKey);
          }
        }
      }
      outputBuilder.put(otherKeyValue.getKey(), resultVal);

      if (backpointers != null) {
        newBackpointerNums[backpointerIndex] = outputBuilder.dimKeyToKeyNum(otherKeyValue.getKey());
        oldBackpointerNums[backpointerIndex] = backpointerKeyNum;
        backpointerIndex++;
      }
    }

    if (backpointers != null) {
      backpointers.setBackpointers(newBackpointerNums, oldBackpointerNums, backpointerIndex, this);
    }

    return outputBuilder.buildNoCopy();
  }

  @Override
  public DenseTensor relabelDimensions(int[] newDimensions) {
    Preconditions.checkArgument(newDimensions.length == getDimensionNumbers().length);
    if (Ordering.natural().isOrdered(Ints.asList(newDimensions))) {
      // If the new dimension labels are in sorted order, then we don't have to
      // resort the outcome and value arrays. This is a big efficiency win if it
      // happens. Note that outcomes and values are (treated as) immutable, and
      // hence we don't need to copy them.
      return new DenseTensor(newDimensions, getDimensionSizes(), values);
    }

    int[] sortedDims = ArrayUtils.copyOf(newDimensions, newDimensions.length);
    Arrays.sort(sortedDims);

    // Figure out the mapping from the new, sorted dimension indices to
    // the current dimension numbers.
    Map<Integer, Integer> currentDimInds = Maps.newHashMap();
    for (int i = 0; i < newDimensions.length; i++) {
      currentDimInds.put(newDimensions[i], i);
    }

    int[] sortedSizes = new int[newDimensions.length];
    int[] newOrder = new int[sortedDims.length];
    for (int i = 0; i < sortedDims.length; i++) {
      newOrder[i] = currentDimInds.get(sortedDims[i]);
      sortedSizes[i] = getDimensionSizes()[currentDimInds.get(sortedDims[i])];
    }

    DenseTensorBuilder builder = new DenseTensorBuilder(sortedDims, sortedSizes);
    Iterator<KeyValue> keyValueIter = keyValueIterator();
    int[] newKey = new int[newOrder.length];
    while (keyValueIter.hasNext()) {
      KeyValue oldKeyValue = keyValueIter.next();
      for (int i = 0; i < newOrder.length; i++) {
        newKey[i] = oldKeyValue.getKey()[newOrder[i]];
      }
      builder.put(newKey, oldKeyValue.getValue());
    }
    return builder.buildNoCopy();
  }

  @Override
  public DenseTensor relabelDimensions(Map<Integer, Integer> relabeling) {
    int[] newDimensions = new int[getDimensionNumbers().length];
    for (int i = 0; i < getDimensionNumbers().length; i++) {
      newDimensions[i] = relabeling.get(getDimensionNumbers()[i]);
    }
    return relabelDimensions(newDimensions);
  }

  @Override
  public DenseTensor replaceValues(double[] newValues) {
    return new DenseTensor(getDimensionNumbers(), getDimensionSizes(), newValues);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    } else if (other instanceof DenseTensor) {
      DenseTensor otherTensor = (DenseTensor) other;
      if (Arrays.equals(otherTensor.getDimensionNumbers(), getDimensionNumbers()) &&
          Arrays.equals(otherTensor.getDimensionSizes(), getDimensionSizes())) {
        for (int i = 0; i < values.length; i++) {
          if (values[i] != otherTensor.values[i]) {
            return false;
          }
        }
        return true;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("[DenseTensor ");
    Iterator<KeyValue> keyValueIter = keyValueIterator();
    while (keyValueIter.hasNext()) {
      KeyValue keyValue = keyValueIter.next();
      if (keyValue.getValue() != 0.0) {
        sb.append(Arrays.toString(keyValue.getKey()));
        sb.append("=");
        sb.append(keyValue.getValue());
        sb.append(", ");
      }
    }
    sb.append("]");
    return sb.toString();
  }

  // /////////////////////////////////////////////////////////////////////
  // Static methods
  // /////////////////////////////////////////////////////////////////////

  /**
   * Constructs a random tensor spanning {@code dimensions} each with size
   * {@code sizes}. Each entry of the tensor is drawn independently at random
   * from a gaussian with {@code mean} and standard deviation {@code stddev}.
   * 
   * @param dimensions
   * @param sizes
   * @param mean
   * @param stddev
   * @return
   */
  public static DenseTensor random(int[] dimensions, int[] sizes, double mean, double stddev) {
    DenseTensorBuilder builder = new DenseTensorBuilder(dimensions, sizes);
    Iterator<KeyValue> keyValueIter = builder.keyValueIterator();
    Random random = Pseudorandom.get();
    while (keyValueIter.hasNext()) {
      builder.put(keyValueIter.next().getKey(), (random.nextGaussian() * stddev) + mean);
    }
    return builder.buildNoCopy();
  }

  /**
   * Gets a tensor where each key has {@code weight}.
   * 
   * @param dimensions
   * @param sizes
   * @return
   */
  public static DenseTensor constant(int[] dimensions, int[] sizes, double weight) {
    DenseTensorBuilder builder = new DenseTensorBuilder(dimensions, sizes, weight);
    return builder.buildNoCopy();
  }
  
  /**
   * Gets a tensor representation of a scalar.
   *  
   * @param value
   * @return
   */
  public static DenseTensor scalar(double value) {
    return new DenseTensor(new int[] {}, new int[] {}, new double[] {value});
  }

  /**
   * Gets a dense copy of {@code tensor}.
   * 
   * @param tensor
   * @return
   */
  public static DenseTensor copyOf(Tensor tensor) {
    if (tensor instanceof DenseTensor) {
      // Tensors are immutable, so there's no reason to copy the input.
      return (DenseTensor) tensor;
    } else {
      DenseTensorBuilder builder = new DenseTensorBuilder(tensor.getDimensionNumbers(),
          tensor.getDimensionSizes());
      double[] otherValues = tensor.getValues();
      for (int i = 0; i < otherValues.length; i++) {
        builder.putByKeyNum(tensor.indexToKeyNum(i), otherValues[i]);
      }
      return builder.buildNoCopy();
    }
  }
}
