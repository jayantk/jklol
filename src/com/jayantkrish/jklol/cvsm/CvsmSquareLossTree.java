package com.jayantkrish.jklol.cvsm;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
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
  public void backpropagateGradient(Tensor treeGradient, CvsmFamily family,
      SufficientStatistics gradient) {
    Tensor predictedValue = getValue();
    Tensor nodeGradient = targets.elementwiseAddition(predictedValue.elementwiseProduct(-1.0));

    Tensor resultGradient = nodeGradient.elementwiseAddition(treeGradient);
    subtree.backpropagateGradient(resultGradient, family, gradient);
  }
  
  @Override
  public double getLoss() {
    Tensor deltas = getValue().elementwiseAddition(targets.elementwiseProduct(-1.0));
    return deltas.innerProduct(deltas).getByDimKey();
  }
}
