package com.jayantkrish.jklol.cvsm.tree;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.cvsm.CvsmGradient;
import com.jayantkrish.jklol.cvsm.lrt.LowRankTensor;
import com.jayantkrish.jklol.cvsm.lrt.TensorLowRankTensor;
import com.jayantkrish.jklol.tensor.Tensor;

public class CvsmExpTree extends AbstractCvsmTree {

  private final CvsmTree subtree;

  private CvsmExpTree(LowRankTensor values, CvsmTree subtree) {
    super(values);
    this.subtree = Preconditions.checkNotNull(subtree);
  }

  public static CvsmExpTree create(CvsmTree subtree) {
    Tensor values = subtree.getValue().getTensor();
    Tensor expValues = values.elementwiseExp();
    return new CvsmExpTree(new TensorLowRankTensor(expValues), subtree);
  }

  @Override
  public List<CvsmTree> getSubtrees() {
    return Arrays.asList(subtree);
  }

  @Override
  public CvsmTree replaceSubtrees(List<CvsmTree> subtrees) {
    Preconditions.checkArgument(subtrees.size() == 1);
    return CvsmExpTree.create(subtrees.get(0));
  }

  @Override
  public void backpropagateGradient(LowRankTensor treeGradient, CvsmGradient gradient) {
    Tensor nodeGradient = getValue().getTensor();
    Tensor treeGradientTensor = treeGradient.getTensor();

    LowRankTensor gradientLowRankTensor = new TensorLowRankTensor(treeGradientTensor.elementwiseProduct(nodeGradient)); 
    subtree.backpropagateGradient(gradientLowRankTensor, gradient);
  }

  @Override
  public double getLoss() {
    return 0;
  }
}

