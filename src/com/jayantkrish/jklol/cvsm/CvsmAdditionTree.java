package com.jayantkrish.jklol.cvsm;

import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.Tensor;

public class CvsmAdditionTree extends AbstractCvsmTree {

  private final CvsmTree left;
  private final CvsmTree right;

  public CvsmAdditionTree(CvsmTree left, CvsmTree right) {
    super(left.getValue().elementwiseAddition(right.getValue()));
    this.left = left;
    this.right = right;
  }

  @Override
  public void backpropagateGradient(Tensor treeGradient, CvsmFamily family, SufficientStatistics gradient) {
    left.backpropagateGradient(treeGradient, family, gradient);
    right.backpropagateGradient(treeGradient, family, gradient);
  }

  @Override
  public double getLoss() {
    return 0;
  }
}
