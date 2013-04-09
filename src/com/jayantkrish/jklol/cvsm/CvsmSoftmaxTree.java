package com.jayantkrish.jklol.cvsm;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.Tensor;

public class CvsmSoftmaxTree extends AbstractCvsmTree {

  private final CvsmTree subtree;

  private CvsmSoftmaxTree(Tensor values, CvsmTree subtree) {
    super(values);
    this.subtree = Preconditions.checkNotNull(subtree);
  }
  
  public static CvsmSoftmaxTree create(CvsmTree subtree) {
    Tensor unnormalizedValues = subtree.getValue().elementwiseExp();
    double partitionFunction = unnormalizedValues.sumOutDimensions(unnormalizedValues.getDimensionNumbers()).getByDimKey();    
    Tensor values = unnormalizedValues.elementwiseProduct(1.0 / partitionFunction);
    
    return new CvsmSoftmaxTree(values, subtree);
  }

  @Override
  public void backpropagateGradient(Tensor treeGradient, CvsmFamily family, SufficientStatistics gradient) {
    Tensor probs = getValue();
    Tensor expectations = probs.elementwiseProduct(treeGradient);
    
    double innerProduct = expectations.sumOutDimensions(expectations.getDimensionNumbers()).getByDimKey();    
    Tensor crossExpectations = probs.elementwiseProduct(innerProduct); 
    
    Tensor subtreeGradient = expectations.elementwiseAddition(crossExpectations.elementwiseProduct(-1.0));
    subtree.backpropagateGradient(subtreeGradient, family, gradient);
  }

  @Override
  public double getLoss() {
    return 0;
  }
}
