package com.jayantkrish.jklol.cvsm.tree;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.cvsm.CvsmGradient;
import com.jayantkrish.jklol.cvsm.lrt.LowRankTensor;
import com.jayantkrish.jklol.cvsm.lrt.TensorLowRankTensor;
import com.jayantkrish.jklol.tensor.Tensor;

public class CvsmKlElementwiseLossTree extends AbstractCvsmTree {
  
  private final Tensor targetDistribution;
  private final CvsmTree subtree;

  public CvsmKlElementwiseLossTree(Tensor targetDistribution, CvsmTree subtree) {
    super(subtree.getValue());
    this.targetDistribution = Preconditions.checkNotNull(targetDistribution);
    this.subtree = Preconditions.checkNotNull(subtree);
    
    Preconditions.checkArgument(Arrays.equals(subtree.getValue().getDimensionNumbers(),
        targetDistribution.getDimensionNumbers()));
  }
  
  @Override
  public List<CvsmTree> getSubtrees() {
    return Arrays.asList(subtree);
  }

  @Override
  public CvsmTree replaceSubtrees(List<CvsmTree> subtrees) {
    Preconditions.checkArgument(subtrees.size() == 1);
    return new CvsmKlLossTree(targetDistribution, subtrees.get(0));
  }

  @Override
  public void backpropagateGradient(LowRankTensor treeGradient, CvsmGradient gradient) {
    Tensor predictedNodeDistribution = getValue().getTensor();

    Tensor oneTerm = targetDistribution.elementwiseProduct(predictedNodeDistribution.elementwiseInverse());
    Tensor zeroTerm = targetDistribution.elementwiseAddition(-1).elementwiseProduct(
        predictedNodeDistribution.elementwiseAddition(-1).elementwiseInverse());
    Tensor nodeGradient = oneTerm.elementwiseAddition(zeroTerm.elementwiseProduct(-1));
    
    subtree.backpropagateGradient(new TensorLowRankTensor(
        nodeGradient.elementwiseAddition(treeGradient.getTensor())), gradient);
  }

  @Override
  public double getLoss() {
    double[] predictedValues = getValue().getTensor().getValues();
    double[] targetValues = targetDistribution.getValues();
    double kl = 0;
    for (int i = 0; i < targetValues.length; i++) {
      if (targetValues[i] == 1) {
        kl += Math.log(predictedValues[i]);
      } else if (targetValues[i] == 0) {
        kl += Math.log(1 - predictedValues[i]);
      } else {
        kl += targetValues[i] * Math.log(predictedValues[i]) + (1 - targetValues[i]) * Math.log(1 - predictedValues[i]);
      }
    }
    return -1.0 * kl;
  }
}
