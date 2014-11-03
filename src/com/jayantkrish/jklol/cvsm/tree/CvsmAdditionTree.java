package com.jayantkrish.jklol.cvsm.tree;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.cvsm.CvsmGradient;
import com.jayantkrish.jklol.cvsm.lrt.LowRankTensor;
import com.jayantkrish.jklol.cvsm.lrt.LowRankTensors;

public class CvsmAdditionTree extends AbstractCvsmTree {

  private final CvsmTree left;
  private final CvsmTree right;

  public CvsmAdditionTree(CvsmTree left, CvsmTree right) {
    super(LowRankTensors.elementwiseAddition(left.getValue(), right.getValue()));
    this.left = left;
    this.right = right;
  }
  
  @Override
  public List<CvsmTree> getSubtrees() {
    return Arrays.asList(left, right);
  }

  @Override
  public CvsmTree replaceSubtrees(List<CvsmTree> subtrees) {
    Preconditions.checkArgument(subtrees.size() == 2);
    return new CvsmAdditionTree(subtrees.get(0), subtrees.get(1));
  }

  @Override
  public void backpropagateGradient(LowRankTensor treeGradient, CvsmGradient gradient) {
    left.backpropagateGradient(treeGradient, gradient);
    right.backpropagateGradient(treeGradient, gradient);
  }

  @Override
  public double getLoss() {
    return 0;
  }
}
