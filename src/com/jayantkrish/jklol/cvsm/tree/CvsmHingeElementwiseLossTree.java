package com.jayantkrish.jklol.cvsm.tree;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.cvsm.CvsmGradient;
import com.jayantkrish.jklol.cvsm.lrt.LowRankTensor;
import com.jayantkrish.jklol.cvsm.lrt.TensorLowRankTensor;
import com.jayantkrish.jklol.tensor.Tensor;

public class CvsmHingeElementwiseLossTree extends AbstractCvsmTree {
  
  private final Tensor targetDistribution;
  private final CvsmTree subtree;

  public CvsmHingeElementwiseLossTree(Tensor targetDistribution, CvsmTree subtree) {
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
    return new CvsmHingeElementwiseLossTree(targetDistribution, subtrees.get(0));
  }

  @Override
  public void backpropagateGradient(LowRankTensor treeGradient, CvsmGradient gradient) {
    Tensor predictedNodeWeights = getValue().getTensor();
    
    Tensor oneOrNegativeOneLabels = targetDistribution.elementwiseAddition(
        targetDistribution.elementwiseAddition(-1));
    Tensor labelWeights = predictedNodeWeights.elementwiseProduct(oneOrNegativeOneLabels);

    Tensor zeroGradientIndicator = labelWeights.findKeysLargerThan(1.0);
    Tensor gradientTensor = oneOrNegativeOneLabels.elementwiseProduct(
        zeroGradientIndicator.elementwiseAddition(-1)).elementwiseProduct(-1);
    
    // System.out.println(Arrays.toString(labelWeights.getValues()));
    // System.out.println(Arrays.toString(gradientTensor.getValues()));
    
    subtree.backpropagateGradient(new TensorLowRankTensor(gradientTensor), gradient);
  }

  @Override
  public double getLoss() {
    double[] predictedValues = getValue().getTensor().getValues();
    double[] targetValues = targetDistribution.getValues();
    double loss = 0;

    for (int i = 0; i < targetValues.length; i++) {
      if (targetValues[i] == 1) {
        loss += Math.max(1 - predictedValues[i], 0);
      } else if (targetValues[i] == 0) {
        loss += Math.max(1 + predictedValues[i], 0);
      } else {
        Preconditions.checkState(false, "target is not zero or one", targetValues);
      }
    }
    return loss;
  }
}
