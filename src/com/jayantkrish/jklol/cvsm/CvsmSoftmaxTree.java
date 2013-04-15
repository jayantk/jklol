package com.jayantkrish.jklol.cvsm;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.Tensor;

public class CvsmSoftmaxTree extends AbstractCvsmTree {

  private final CvsmTree subtree;

  private CvsmSoftmaxTree(LowRankTensor values, CvsmTree subtree) {
    super(values);
    this.subtree = Preconditions.checkNotNull(subtree);
  }
  
  public static CvsmSoftmaxTree create(CvsmTree subtree) {
    Tensor unnormalizedValues = subtree.getValue().getTensor().elementwiseExp();
    double partitionFunction = unnormalizedValues.sumOutDimensions(unnormalizedValues.getDimensionNumbers()).getByDimKey();    
    Tensor values = unnormalizedValues.elementwiseProduct(1.0 / partitionFunction);
    
    return new CvsmSoftmaxTree(LowRankTensor.vector(values), subtree);
  }

  @Override
  public void backpropagateGradient(LowRankTensor treeGradient, CvsmFamily family, SufficientStatistics gradient) {
    Tensor probs = getValue().getTensor();
    Tensor expectations = probs.elementwiseProduct(treeGradient.getTensor());
    
    double innerProduct = expectations.sumOutDimensions(expectations.getDimensionNumbers()).getByDimKey();    
    Tensor crossExpectations = probs.elementwiseProduct(innerProduct); 
    
    Tensor subtreeGradient = expectations.elementwiseAddition(crossExpectations.elementwiseProduct(-1.0));
    subtree.backpropagateGradient(LowRankTensor.vector(subtreeGradient), family, gradient);
  }

  @Override
  public double getLoss() {
    return 0;
  }
}
