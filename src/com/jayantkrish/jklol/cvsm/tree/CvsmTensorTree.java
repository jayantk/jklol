package com.jayantkrish.jklol.cvsm.tree;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.cvsm.CvsmGradient;
import com.jayantkrish.jklol.cvsm.lrt.LowRankTensor;

public class CvsmTensorTree extends AbstractCvsmTree {
  
  private final String valueName;
  
  public CvsmTensorTree(String valueName, LowRankTensor value) {
    super(value);
    this.valueName = Preconditions.checkNotNull(valueName);
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
    Preconditions.checkArgument(Arrays.equals(treeGradient.getDimensionNumbers(),
        getValue().getDimensionNumbers()));
    gradient.incrementValue(valueName, treeGradient);
  }

  @Override
  public double getLoss() {
    return 0.0;
  }
}
