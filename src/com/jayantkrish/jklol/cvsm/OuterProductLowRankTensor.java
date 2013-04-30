package com.jayantkrish.jklol.cvsm;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.tensor.Tensor;

public class OuterProductLowRankTensor extends AbstractLowRankTensor {
  private static final long serialVersionUID = 1L;
  
  private final LowRankTensor left;
  private final LowRankTensor right;

  private OuterProductLowRankTensor(int[] dimensionNums, int[] dimensionSizes,
      LowRankTensor left, LowRankTensor right) {
    super(dimensionNums, dimensionSizes);
    this.left = Preconditions.checkNotNull(left);
    this.right = Preconditions.checkNotNull(right);
  }
  
  public static OuterProductLowRankTensor create(LowRankTensor left, LowRankTensor right) {
    SortedSet<Integer> dims = Sets.newTreeSet();
    dims.addAll(Ints.asList(left.getDimensionNumbers()));
    dims.addAll(Ints.asList(right.getDimensionNumbers()));
    int[] dimArray = Ints.toArray(dims);
    int[] dimSizes = new int[dimArray.length];
    for (int i = 0; i < dimArray.length; i++) {
      int dim = dimArray[i];
      int leftInd = Ints.indexOf(left.getDimensionNumbers(), dim);
      int rightInd = Ints.indexOf(right.getDimensionNumbers(), dim);
      
      if (leftInd != -1) {
        dimSizes[i] = left.getDimensionSizes()[leftInd];
      } else {
        dimSizes[i] = right.getDimensionSizes()[rightInd];
      }
    }

    return new OuterProductLowRankTensor(dimArray, dimSizes, left, right);
  }
  
  public LowRankTensor getLeft() {
    return left;
  }
  
  public LowRankTensor getRight() {
    return right;
  }

  @Override
  public Tensor getTensor() {
    return left.getTensor().outerProduct(right.getTensor());
  }
  
  @Override
  public double getByDimKey(int ... key) {
    Preconditions.checkArgument(key.length == getDimensionNumbers().length);
    List<LowRankTensor> subtensors = Arrays.asList(left, right);
    int[] myDims = getDimensionNumbers();
    double value = 1.0;
    for (int i = 0; i < subtensors.size(); i++) {
      LowRankTensor tensor = subtensors.get(i);
      
      int[] tensorDims = tensor.getDimensionNumbers();
      int[] tensorKey = new int[tensorDims.length];
      for (int j = 0; j < tensorKey.length; j++) {
        int ind = Ints.indexOf(myDims, tensorDims[j]);
        tensorKey[j] = key[ind];
      }

      value *= tensor.getByDimKey(tensorKey);
    }
    return value;
  }

  @Override
  public LowRankTensor relabelDimensions(BiMap<Integer, Integer> relabeling) {
    return OuterProductLowRankTensor.create(left.relabelDimensions(relabeling),
        right.relabelDimensions(relabeling));
  }

  @Override
  public LowRankTensor innerProduct(LowRankTensor other) {
    int[] otherDims = other.getDimensionNumbers();
    Set<Integer> otherDimsSet = Sets.newHashSet(Ints.asList(otherDims));
    Set<Integer> myDims = Sets.newHashSet(Ints.asList(getDimensionNumbers()));
    Set<Integer> myLeftDims = Sets.newHashSet(Ints.asList(left.getDimensionNumbers()));
    Set<Integer> myRightDims = Sets.newHashSet(Ints.asList(right.getDimensionNumbers()));
    
    if (myLeftDims.containsAll(otherDimsSet)) {
      LowRankTensor newLeft = left.innerProduct(other);
      return LowRankTensors.outerProduct(newLeft, right);
    } else if (myRightDims.containsAll(otherDimsSet)) {
      LowRankTensor newRight = right.innerProduct(other);
      return LowRankTensors.outerProduct(left, newRight);
    } else if (other instanceof OuterProductLowRankTensor) {
      OuterProductLowRankTensor outer = (OuterProductLowRankTensor) other;
      
      Set<Integer> otherLeftDims = Sets.newHashSet(Ints.asList(outer.left.getDimensionNumbers()));
      Set<Integer> otherRightDims = Sets.newHashSet(Ints.asList(outer.right.getDimensionNumbers()));
      
      if (myLeftDims.containsAll(otherLeftDims) && myRightDims.containsAll(otherRightDims)) {
        LowRankTensor newLeft = left.innerProduct(outer.left);
        LowRankTensor newRight = right.innerProduct(outer.right);
        
        return LowRankTensors.outerProduct(newLeft, newRight);
      }
    } else if (myDims.equals(otherDimsSet)) {
      return other.innerProduct(this);
    }

    throw new UnsupportedOperationException("Cannot compute inner product.");
  }
  
  @Override
  public OuterProductLowRankTensor elementwiseProduct(double value) {
    return OuterProductLowRankTensor.create(left.elementwiseProduct(value), right);
  }
}
