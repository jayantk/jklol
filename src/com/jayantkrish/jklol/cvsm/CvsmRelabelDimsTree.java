package com.jayantkrish.jklol.cvsm;

import com.google.common.collect.BiMap;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.Tensor;

public class CvsmRelabelDimsTree extends AbstractCvsmTree {
  
  private final CvsmTree subtree;
  private final BiMap<Integer, Integer> relabeling;
  private final BiMap<Integer, Integer> inverseRelabeling;
  
  public CvsmRelabelDimsTree(CvsmTree subtree, BiMap<Integer, Integer> subtreeToRootRelabeling) {
    super(subtree.getValue().relabelDimensions(subtreeToRootRelabeling));
    this.subtree = subtree;
    this.relabeling = subtreeToRootRelabeling;
    this.inverseRelabeling = subtreeToRootRelabeling.inverse();
  }

  @Override
  public void backpropagateGradient(Tensor treeGradient, CvsmFamily family,
      SufficientStatistics gradient) {
    subtree.backpropagateGradient(treeGradient.relabelDimensions(inverseRelabeling),
        family, gradient);
  }

  @Override
  public double getLoss() {
    return 0;
  }
}
