package com.jayantkrish.jklol.cvsm;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.Tensor;

public class CvsmLogisticTree extends AbstractCvsmTree {
  
  private final CvsmTree subtree;
  
  public CvsmLogisticTree(CvsmTree subtree) {
    super(new TensorLowRankTensor(subtree.getValue().getTensor().elementwiseProduct(-1.0)
        .elementwiseExp().elementwiseAddition(1.0).elementwiseInverse()));
    this.subtree = Preconditions.checkNotNull(subtree);
  }

  @Override
  public void backpropagateGradient(LowRankTensor treeGradient, CvsmFamily family, SufficientStatistics gradient) {
    Tensor value = getValue().getTensor();
    Tensor nodeGradient = value.elementwiseProduct(value.elementwiseProduct(-1.0).elementwiseAddition(1.0));
    Tensor subtreeGradient = nodeGradient.elementwiseProduct(treeGradient.getTensor());
    
    subtree.backpropagateGradient(new TensorLowRankTensor(subtreeGradient), family, gradient);
  }

  @Override
  public double getLoss() {
    return 0;
  }
}
