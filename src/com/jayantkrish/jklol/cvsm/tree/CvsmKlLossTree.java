package com.jayantkrish.jklol.cvsm.tree;

import java.util.Arrays;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.cvsm.CvsmGradient;
import com.jayantkrish.jklol.cvsm.lrt.LowRankTensor;
import com.jayantkrish.jklol.cvsm.lrt.TensorLowRankTensor;
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
  public void backpropagateGradient(LowRankTensor treeGradient, CvsmGradient gradient) {
    Tensor nodeDistribution = getValue().getTensor();
    Tensor nodeGradient = targetDistribution.elementwiseProduct(nodeDistribution.elementwiseInverse());
    subtree.backpropagateGradient(new TensorLowRankTensor(nodeGradient.elementwiseAddition(treeGradient.getTensor())),
        gradient);
  }

  @Override
  public double getLoss() {
    double[] predictedValues = getValue().getTensor().getValues();
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
