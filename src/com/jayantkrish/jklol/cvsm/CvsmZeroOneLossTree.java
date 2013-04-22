package com.jayantkrish.jklol.cvsm;

import java.util.Arrays;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.Backpointers;
import com.jayantkrish.jklol.tensor.Tensor;

public class CvsmZeroOneLossTree extends AbstractCvsmTree {

  private final Tensor targetDistribution;

  public CvsmZeroOneLossTree(Tensor targetDistribution, CvsmTree subtree) {
    super(subtree.getValue());
    this.targetDistribution = Preconditions.checkNotNull(targetDistribution);
    
    Preconditions.checkArgument(Arrays.equals(subtree.getValue().getDimensionNumbers(),
        targetDistribution.getDimensionNumbers()));
  }

  @Override
  public void backpropagateGradient(LowRankTensor treeGradient, CvsmFamily family,
      SufficientStatistics gradient) {
    throw new UnsupportedOperationException("Cannot propagate gradient of zero-one loss.");
  }

  @Override
  public double getLoss() {
    Tensor predicted = getValue().getTensor();
    Backpointers backpointers = new Backpointers();
    predicted.maxOutDimensions(Ints.asList(predicted.getDimensionNumbers()), backpointers);
    
    Tensor zeroOnePredictions = backpointers.getOldKeyIndicatorTensor();
    
    return zeroOnePredictions.innerProduct(targetDistribution).getByDimKey() 
        - targetDistribution.getL2Norm();
  }
}
