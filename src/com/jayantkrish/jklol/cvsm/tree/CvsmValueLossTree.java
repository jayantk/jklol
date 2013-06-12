package com.jayantkrish.jklol.cvsm.tree;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.cvsm.CvsmGradient;
import com.jayantkrish.jklol.cvsm.lrt.LowRankTensor;
import com.jayantkrish.jklol.cvsm.lrt.TensorLowRankTensor;
import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.tensor.Tensor;

/**
 * A loss function where the value of the subtree is the loss.
 * 
 * @author jayantk
 */
public class CvsmValueLossTree extends AbstractCvsmTree {
  
  private final CvsmTree subtree;

  public CvsmValueLossTree(CvsmTree subtree) {
    super(subtree.getValue());
    Preconditions.checkArgument(subtree.getValue().getDimensionNumbers().length == 0);
    this.subtree = subtree;
  }
  
  @Override
  public List<CvsmTree> getSubtrees() {
    return Arrays.asList(subtree);
  }

  @Override
  public CvsmTree replaceSubtrees(List<CvsmTree> subtrees) {
    Preconditions.checkArgument(subtrees.size() == 1);
    return new CvsmValueLossTree(subtrees.get(0));
  }

  @Override
  public void backpropagateGradient(LowRankTensor treeGradient, CvsmGradient gradient) {
    // Backpropagating a gradient of -1 gets the negative gradient,
    // which is what we want to minimize the loss.
    Tensor gradientTensor = DenseTensor.constant(treeGradient.getDimensionNumbers(),
        treeGradient.getDimensionSizes(), -1.0);
    subtree.backpropagateGradient(new TensorLowRankTensor(gradientTensor), gradient);
  }
  
  @Override
  public double getLoss() {
    return getValue().getByDimKey();
  }
}
