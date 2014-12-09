package com.jayantkrish.jklol.cvsm.tree;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.cvsm.CvsmGradient;
import com.jayantkrish.jklol.cvsm.lrt.LowRankTensor;
import com.jayantkrish.jklol.cvsm.lrt.TensorLowRankTensor;
import com.jayantkrish.jklol.tensor.Tensor;

public class CvsmLaplaceSigmoidTree extends AbstractCvsmTree {

  private CvsmTree subtree;
  private double smoothness;
  
  public CvsmLaplaceSigmoidTree(CvsmTree subtree, double smoothness) {
    super(new TensorLowRankTensor(subtree.getValue().getTensor().elementwiseLaplaceSigmoid(smoothness)));
    
    this.subtree = subtree;
    this.smoothness = smoothness;
  }

  @Override
  public List<CvsmTree> getSubtrees() {
    return Arrays.asList(subtree);
  }

  @Override
  public CvsmTree replaceSubtrees(List<CvsmTree> subtrees) {
    Preconditions.checkArgument(subtrees.size() == 1);
    return new CvsmLaplaceSigmoidTree(subtrees.get(0), smoothness);
  }

  @Override
  public void backpropagateGradient(LowRankTensor treeGradient, CvsmGradient gradient) {
    Tensor value = getValue().getTensor();
    Tensor nodeGradient = value.elementwiseAbs().elementwiseProduct(-1 * smoothness).elementwiseAddition(smoothness);
    Tensor subtreeGradient = nodeGradient.elementwiseProduct(treeGradient.getTensor());

    subtree.backpropagateGradient(new TensorLowRankTensor(subtreeGradient), gradient);
  }

  @Override
  public double getLoss() {
    return 0;
  }
}
