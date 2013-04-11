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
    double[] predictedValues = getValue().getValues();
    double[] targetValues = targetDistribution.getValues();
    double kl = 0;
    for (int i = 0; i < targetValues.length; i++) {
      if (targetValues[i] != 0.0) {
        kl += targetValues[i] * (Math.log(targetValues[i]) - Math.log(predictedValues[i]));
      }
    }
    return kl;
  }
}
