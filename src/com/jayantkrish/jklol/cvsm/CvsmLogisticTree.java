package com.jayantkrish.jklol.cvsm;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.Tensor;

public class CvsmLogisticTree extends AbstractCvsmTree {
  
  private final CvsmTree subtree;
  
  public CvsmLogisticTree(CvsmTree subtree) {
    super(subtree.getValue().elementwiseProduct(-1.0).elementwiseExp().elementwiseAddition(1.0).elementwiseInverse());
    this.subtree = Preconditions.checkNotNull(subtree);
  }

  @Override
  public void backpropagateGradient(Tensor treeGradient, CvsmFamily family, SufficientStatistics gradient) {
    Tensor value = getValue();
    Tensor nodeGradient = value.elementwiseProduct(value.elementwiseProduct(-1.0).elementwiseAddition(1.0));
    Tensor subtreeGradient = nodeGradient.elementwiseProduct(treeGradient);
    
    subtree.backpropagateGradient(subtreeGradient, family, gradient);
  }

  @Override
  public double getLoss() {
    return 0;
  }
}
