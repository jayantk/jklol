package com.jayantkrish.jklol.cvsm;

import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.ArrayUtils;

public class CvsmReduceTree extends AbstractCvsmTree {
  
  private final int[] dimsToEliminate;
  private final CvsmTree subtree;

  public CvsmReduceTree(int[] dimsToEliminate, CvsmTree subtree) {
    super(subtree.getValue().sumOutDimensions(dimsToEliminate));
    this.dimsToEliminate = ArrayUtils.copyOf(dimsToEliminate, dimsToEliminate.length);
    this.subtree = subtree;

    getValue();
  }

  @Override
  public void backpropagateGradient(Tensor treeGradient, CvsmFamily family,
      SufficientStatistics gradient) {
    Tensor subtreeValue = subtree.getValue();
    Tensor ones = DenseTensor.constant(subtreeValue.getDimensionNumbers(),
        subtreeValue.getDimensionSizes(), 1.0);

    subtree.backpropagateGradient(ones.elementwiseProduct(treeGradient), family, gradient);
  }

  @Override
  public double getLoss() {
    return 0;
  }
}
