package com.jayantkrish.jklol.cvsm.tree;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.cvsm.CvsmGradient;
import com.jayantkrish.jklol.cvsm.lrt.LowRankTensor;

public class CvsmConstantTree extends AbstractCvsmTree {
  
  public CvsmConstantTree(LowRankTensor value) {
    super(value);
  }

  @Override
  public List<CvsmTree> getSubtrees() {
    return Arrays.asList();
  }

  @Override
  public CvsmTree replaceSubtrees(List<CvsmTree> subtrees) {
    Preconditions.checkArgument(subtrees.size() == 0);
    return this;
  }

  @Override
  public void backpropagateGradient(LowRankTensor treeGradient, CvsmGradient gradient) {
  }

  @Override
  public double getLoss() {
    return 0.0;
  }
}
