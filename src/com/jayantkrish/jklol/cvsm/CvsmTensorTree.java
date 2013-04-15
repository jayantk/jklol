package com.jayantkrish.jklol.cvsm;

import java.util.Arrays;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

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
    family.incrementValueSufficientStatistics(valueName, getValue(), treeGradient, gradient, 1.0);
  }

  @Override
  public double getLoss() {
    return 0.0;
  }
}
