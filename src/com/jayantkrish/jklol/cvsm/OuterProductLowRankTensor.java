package com.jayantkrish.jklol.cvsm;

import java.util.Set;

import com.google.common.collect.BiMap;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.tensor.Tensor;

public class OuterProductLowRankTensor extends AbstractLowRankTensor {
  
  private final LowRankTensor left;
  private final LowRankTensor right;

  public OuterProductLowRankTensor(int[] dimensionNums, int[] dimensionSizes) {
    super(dimensionNums, dimensionSizes);
    // TODO Auto-generated constructor stub
  }

  @Override
  public Tensor getTensor() {
    return left.getTensor().outerProduct(right.getTensor());
  }

  @Override
  public LowRankTensor relabelDimensions(BiMap<Integer, Integer> relabeling) {
    return new OuterProductLowRankTensor(left.relabelDimensions(relabeling),
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
      return new OuterProductLowRankTensor(newLeft, right);
    } else if (myRightDims.containsAll(otherDimsSet)) {
      LowRankTensor newRight = right.innerProduct(other);
      return new OuterProductLowRankTensor(left, newRight);
    } else if (other instanceof OuterProductLowRankTensor) {
      OuterProductLowRankTensor outer = (OuterProductLowRankTensor) other;
      
      Set<Integer> otherLeftDims = Sets.newHashSet(Ints.asList(outer.left.getDimensionNumbers()));
      Set<Integer> otherRightDims = Sets.newHashSet(Ints.asList(outer.right.getDimensionNumbers()));
      
      if (myLeftDims.containsAll(otherLeftDims) && myRightDims.containsAll(otherRightDims)) {
        LowRankTensor newLeft = left.innerProduct(outer.left);
        LowRankTensor newRight = right.innerProduct(outer.right);
        
        return new OuterProductLowRankTensor(newLeft, newRight);
      }
    } else if (myDims.equals(otherDimsSet)) {
      return other.innerProduct(this);
    }

    throw new UnsupportedOperationException("Cannot compute inner product.");
  }

  @Override
  public LowRankTensor outerProduct(LowRankTensor other) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public LowRankTensor elementwiseAddition(LowRankTensor other) {
    // TODO Auto-generated method stub
    return null;
  }
}
