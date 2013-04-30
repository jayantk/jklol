package com.jayantkrish.jklol.cvsm.tree;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.cvsm.CvsmGradient;
import com.jayantkrish.jklol.cvsm.lrt.LowRankTensor;
import com.jayantkrish.jklol.cvsm.lrt.TensorLowRankTensor;
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
    
    return new CvsmSoftmaxTree(new TensorLowRankTensor(values), subtree);
  }

  @Override
  public void backpropagateGradient(LowRankTensor treeGradient, CvsmGradient gradient) {
    Tensor probs = getValue().getTensor();
    Tensor expectations = probs.elementwiseProduct(treeGradient.getTensor());
    
    double innerProduct = expectations.sumOutDimensions(expectations.getDimensionNumbers()).getByDimKey();    
    Tensor crossExpectations = probs.elementwiseProduct(innerProduct); 
    
    Tensor subtreeGradient = expectations.elementwiseAddition(crossExpectations.elementwiseProduct(-1.0));
    subtree.backpropagateGradient(new TensorLowRankTensor(subtreeGradient), gradient);
  }

  @Override
  public double getLoss() {
    return 0;
  }
}
