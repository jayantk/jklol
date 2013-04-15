package com.jayantkrish.jklol.cvsm;

import java.util.Arrays;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.Tensor;

public class CvsmTensorTree extends AbstractCvsmTree {
  
  private final String valueName;
  
  public CvsmTensorTree(String valueName, LowRankTensor value) {
    super(value);
    this.valueName = Preconditions.checkNotNull(valueName);
  }

  @Override
  public void backpropagateGradient(LowRankTensor treeGradient, CvsmFamily family,
      SufficientStatistics gradient) {
    Preconditions.checkArgument(Arrays.equals(treeGradient.getDimensionNumbers(),
        getValue().getDimensionNumbers()));
    
    LowRankTensor value = getValue();
    int[] valueDims = getValue().getDimensionNumbers();
    for (int k = 0; k < value.getRank(); k++) {
      for (int i = 0; i < valueDims.length; i++) {
        // tree gradient inner producted with all dims 
        LowRankTensor curGradient = treeGradient;

        for (int j = 0; j < valueDims.length; j++) {
          if (j == i) {
            continue;
          }
          curGradient = curGradient.innerProduct(value.getVector(j, k));
        }
        
        // TODO: increment the gradient of these parameters.
      }
    }
    
    family.incrementValueSufficientStatistics(valueName, treeGradient, gradient, 1.0);
  }

  @Override
  public double getLoss() {
    return 0.0;
  }
}
