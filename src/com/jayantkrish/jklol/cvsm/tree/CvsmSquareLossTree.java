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
 * Adds a square loss term, encouraging the tensor produced by this
 * node's subtree to agree with a particular tensor of targets.
 * 
 * @author jayant
 */
public class CvsmSquareLossTree extends AbstractCvsmTree {

  private final Tensor targets;
  private final CvsmTree subtree;

  public CvsmSquareLossTree(Tensor targets, CvsmTree subtree) {
    super(subtree.getValue());
    this.targets = Preconditions.checkNotNull(targets);
    this.subtree = Preconditions.checkNotNull(subtree);
  }

  @Override
  public List<CvsmTree> getSubtrees() {
    return Arrays.asList(subtree);
  }

  @Override
  public CvsmTree replaceSubtrees(List<CvsmTree> subtrees) {
    Preconditions.checkArgument(subtrees.size() == 1);
    return new CvsmSquareLossTree(targets, subtrees.get(0));
  }

  private Tensor getTargets(int dimensionality) {
    if (dimensionality != 0) {
      return targets;
    } else {
      return new DenseTensor(new int[] {}, new int[] {}, new double[] {targets.getByDimKey(new int[] {0})});
    }
  }

  @Override
  public void backpropagateGradient(LowRankTensor treeGradient, CvsmGradient gradient) {
    Tensor predictedValue = getValue().getTensor();
    Tensor nodeGradient = getTargets(predictedValue.getDimensionSizes().length).elementwiseAddition(predictedValue.elementwiseProduct(-1.0));
   
    Tensor resultGradient = nodeGradient.elementwiseAddition(treeGradient.getTensor());
    subtree.backpropagateGradient(new TensorLowRankTensor(resultGradient), gradient);
  }
  
  @Override
  public double getLoss() {
    Tensor valueTensor = getValue().getTensor();
    Tensor deltas = valueTensor.elementwiseAddition(getTargets(valueTensor.getDimensionSizes().length).elementwiseProduct(-1.0));
    return deltas.innerProduct(deltas).getByDimKey();
  }
}
