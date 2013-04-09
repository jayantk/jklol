package com.jayantkrish.jklol.cvsm;

import java.util.Arrays;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.Tensor;

public class CvsmKlLossTree extends AbstractCvsmTree {
  
  private final Tensor targetDistribution;
  private final CvsmTree subtree;

  public CvsmKlLossTree(Tensor targetDistribution, CvsmTree subtree) {
    super(subtree.getValue());
    this.targetDistribution = Preconditions.checkNotNull(targetDistribution);
    this.subtree = Preconditions.checkNotNull(subtree);
    
    Preconditions.checkArgument(Arrays.equals(subtree.getValue().getDimensionNumbers(),
        targetDistribution.getDimensionNumbers()));
  }

  @Override
  public void backpropagateGradient(Tensor treeGradient, CvsmFamily family, SufficientStatistics gradient) {
    Tensor nodeDistribution = getValue();
    Tensor nodeGradient = targetDistribution.elementwiseProduct(nodeDistribution.elementwiseInverse());
    subtree.backpropagateGradient(nodeGradient.elementwiseAddition(treeGradient), family, gradient);
  }

  @Override
  public double getLoss() {
    return getValue().elementwiseLog().elementwiseProduct(targetDistribution).elementwiseProduct(-1.0).getTrace();
  }
}
