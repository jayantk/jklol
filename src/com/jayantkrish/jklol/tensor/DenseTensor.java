package com.jayantkrish.jklol.tensor;

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
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.tensor.TensorProtos.DenseTensorProto;
import com.jayantkrish.jklol.tensor.TensorProtos.TensorProto;
import com.jayantkrish.jklol.util.HeapUtils;
import com.jayantkrish.jklol.util.IntegerArrayIterator;

/**
 * Immutable tensor, represented densely. The dense representation is faster
 * than {@link SparseTensor}, but requires more memory.
 * 
 * @author jayantk
 */
public class DenseTensor extends DenseTensorBase implements Tensor {
  
  private static Random random = new Random();

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
  public DenseTensor elementwiseProduct(Tensor other) {
    return doElementwise(other, Operation.PRODUCT);
  }
  
  @Override
  public DenseTensor outerProduct(Tensor other) {
    throw new UnsupportedOperationException("Not yet implemented.");
  }

  @Override
  public DenseTensor elementwiseAddition(Tensor other) {
    return doElementwise(other, Operation.SUM);
  }

  @Override
  public DenseTensor elementwiseMaximum(Tensor other) {
    return doElementwise(other, Operation.MAX);
  }

  private enum Operation {
    PRODUCT, SUM, MAX
  };

  /**
   * Performs elementwise operations, like products, sums and maxes.
   * 
   * @param other
   * @param op
   * @return
   */
  private DenseTensor doElementwise(Tensor other, Operation op) {
    DenseTensorBuilder outputBuilder = new DenseTensorBuilder(getDimensionNumbers(),
        getDimensionSizes());
    if (op != Operation.PRODUCT) {
      outputBuilder.increment(this);
    }

    // Maps a key of other into a partial key of this.
    int[] dimensionMapping = getDimensionMapping(other.getDimensionNumbers());
    int[] partialKey = Arrays.copyOf(getDimensionSizes(), getDimensionSizes().length);
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
        switch (op) {
        case PRODUCT:
          outputBuilder.values[baseOffset + keyOffsets[i]] = otherKeyValue.getValue();
          break;
        case SUM:
          outputBuilder.values[baseOffset + keyOffsets[i]] += otherKeyValue.getValue();
          break;
        case MAX:
          outputBuilder.values[baseOffset + keyOffsets[i]] = Math.max(otherKeyValue.getValue(),
              outputBuilder.values[baseOffset + keyOffsets[i]]);
          break;
        }
      }
    }

    if (op == Operation.PRODUCT) {
      outputBuilder.multiply(this);
    }
    return outputBuilder.buildNoCopy();
  }

  @Override
  public DenseTensor elementwiseInverse() {
    DenseTensorBuilder outputBuilder = new DenseTensorBuilder(getDimensionNumbers(),
        getDimensionSizes());
    for (int i = 0; i < values.length; i++) {
      outputBuilder.values[i] = (values[i] == 0) ? 0 : 1.0 / values[i];
    }
    return outputBuilder.buildNoCopy();
  }

  @Override
  public DenseTensor elementwiseLog() {
    DenseTensorBuilder outputBuilder = new DenseTensorBuilder(getDimensionNumbers(),
        getDimensionSizes());
    for (int i = 0; i < values.length; i++) {
      outputBuilder.values[i] = Math.log(values[i]);
    }
    return outputBuilder.buildNoCopy();
  }

  @Override
  public DenseTensor elementwiseExp() {
    DenseTensorBuilder outputBuilder = new DenseTensorBuilder(getDimensionNumbers(),
        getDimensionSizes());
    for (int i = 0; i < values.length; i++) {
      outputBuilder.values[i] = Math.exp(values[i]);
    }
    return outputBuilder.buildNoCopy();
  }

  @Override
  public DenseTensor sumOutDimensions(Collection<Integer> dimensionsToEliminate) {
    return reduceDimensions(dimensionsToEliminate, true);
  }

  @Override
  public DenseTensor maxOutDimensions(Collection<Integer> dimensionsToEliminate) {
    return reduceDimensions(dimensionsToEliminate, false);
  }

  /**
   * Performs reduction operations which eliminate some subset of the existing
   * dimensions.
   * 
   * @param dimensionsToEliminate
   * @param useSum
   * @return
   */
  private DenseTensor reduceDimensions(Collection<Integer> dimensionsToEliminate, boolean useSum) {
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

    // Maps a key of the reduced tensor into a partial key of this.
    int[] dimensionMapping = getDimensionMapping(outputBuilder.getDimensionNumbers());
    int[] partialKey = new int[getDimensionNumbers().length];
    Arrays.fill(partialKey, -1);
    Iterator<KeyValue> otherKeyValues = outputBuilder.keyValueIterator();
    while (otherKeyValues.hasNext()) {
      KeyValue otherKeyValue = otherKeyValues.next();
      for (int i = 0; i < otherKeyValue.getKey().length; i++) {
        partialKey[dimensionMapping[i]] = otherKeyValue.getKey()[i];
      }

      Iterator<int[]> myKeyIterator = new SliceIndexIterator(getDimensionSizes(), partialKey);
      double resultVal = (useSum) ? 0.0 : Double.NEGATIVE_INFINITY;
      while (myKeyIterator.hasNext()) {
        if (useSum) {
          resultVal += getByDimKey(myKeyIterator.next());
        } else {
          resultVal = Math.max(resultVal, getByDimKey(myKeyIterator.next()));
        }
      }
      outputBuilder.put(otherKeyValue.getKey(), resultVal);
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

    int[] sortedDims = Arrays.copyOf(newDimensions, newDimensions.length);
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
  public long[] getLargestValues(int n) {
    return HeapUtils.findLargestItemIndexes(values, n);
  }
  
  @Override
  public TensorProto toProto() {
    TensorProto.Builder builder = TensorProto.newBuilder();
    builder.setType(TensorProto.TensorType.DENSE);
    
    DenseTensorProto.Builder denseTensorBuilder = builder.getDenseTensorBuilder();
    denseTensorBuilder.setDimensions(getDimensionProto());
    denseTensorBuilder.addAllValue(Doubles.asList(values));
    return builder.build();
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
  
  ///////////////////////////////////////////////////////////////////////
  // Static methods
  ///////////////////////////////////////////////////////////////////////
  
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

  /**
   * Creates a {@code DenseTensor} from its serialization as a protocol buffer.
   * 
   * @param proto
   * @return
   */
  public static DenseTensor fromProto(DenseTensorProto proto) {
    Preconditions.checkArgument(proto.hasDimensions());
    int[] dimensionNums = AbstractTensorBase.parseDimensionsFromProto(proto.getDimensions());
    int[] sizes = AbstractTensorBase.parseSizesFromProto(proto.getDimensions());
    Preconditions.checkArgument(dimensionNums.length == sizes.length);
    
    double[] values = Doubles.toArray(proto.getValueList());
    return new DenseTensor(dimensionNums, sizes, values);
  }
}
