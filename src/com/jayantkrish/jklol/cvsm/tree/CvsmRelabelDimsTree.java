package com.jayantkrish.jklol.cvsm.tree;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.jayantkrish.jklol.cvsm.CvsmGradient;
import com.jayantkrish.jklol.cvsm.lrt.LowRankTensor;

public class CvsmRelabelDimsTree extends AbstractCvsmTree {
  
  private final CvsmTree subtree;
  private final BiMap<Integer, Integer> inverseRelabeling;
  
  public CvsmRelabelDimsTree(CvsmTree subtree, BiMap<Integer, Integer> subtreeToRootRelabeling) {
    super(subtree.getValue().relabelDimensions(subtreeToRootRelabeling));
    this.subtree = subtree;
    this.inverseRelabeling = subtreeToRootRelabeling.inverse();
  }
  
  @Override
  public List<CvsmTree> getSubtrees() {
    return Arrays.asList(subtree);
  }

  @Override
  public CvsmTree replaceSubtrees(List<CvsmTree> subtrees) {
    Preconditions.checkArgument(subtrees.size() == 1);
    return new CvsmRelabelDimsTree(subtrees.get(0), inverseRelabeling.inverse());
  }

  @Override
  public void backpropagateGradient(LowRankTensor treeGradient, CvsmGradient gradient) {
    subtree.backpropagateGradient(treeGradient.relabelDimensions(inverseRelabeling), gradient);
  }

  @Override
  public double getLoss() {
    return 0;
  }
}
