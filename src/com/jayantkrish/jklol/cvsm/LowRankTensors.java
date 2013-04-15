package com.jayantkrish.jklol.cvsm;

public class LowRankTensors {
  
  public static LowRankTensor outerProduct(LowRankTensor left, LowRankTensor right) {
    if (left.getDimensionNumbers().length == 0) {
      double leftValue = left.getTensor().getByDimKey();
      return right.elementwiseProduct(leftValue);
    } else if (right.getDimensionNumbers().length == 0) {
      double rightValue = right.getTensor().getByDimKey();
      return left.elementwiseProduct(rightValue);
    } else {
      return OuterProductLowRankTensor.create(left, right);
    }
  }
  
  public static LowRankTensor elementwiseAddition(LowRankTensor left, LowRankTensor right) {
    return SumLowRankTensor.create(new LowRankTensor[] {left, right});
  }
  
  private LowRankTensors() {
    // Prevent instantiation.
  }
}
