package com.jayantkrish.jklol.cvsm.tree;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.cvsm.CvsmGradient;
import com.jayantkrish.jklol.cvsm.lrt.LowRankTensor;
import com.jayantkrish.jklol.tensor.Backpointers;
import com.jayantkrish.jklol.tensor.Tensor;

public class CvsmZeroOneLossTree extends AbstractCvsmTree {

  private final Tensor targetDistribution;
  private final CvsmTree subtree;

  public CvsmZeroOneLossTree(Tensor targetDistribution, CvsmTree subtree) {
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
    return new CvsmZeroOneLossTree(targetDistribution, subtrees.get(0));
  }

  @Override
  public void backpropagateGradient(LowRankTensor treeGradient, CvsmGradient gradient) {
    throw new UnsupportedOperationException("Cannot propagate gradient of zero-one loss.");
  }

  @Override
  public double getLoss() {
    Tensor predicted = getValue().getTensor();
    Backpointers backpointers = new Backpointers();
    predicted.maxOutDimensions(Ints.asList(predicted.getDimensionNumbers()), backpointers);

    Tensor zeroOnePredictions = backpointers.getOldKeyIndicatorTensor();

    return targetDistribution.getL2Norm() - 
        zeroOnePredictions.innerProduct(targetDistribution).getByDimKey();
  }
}
