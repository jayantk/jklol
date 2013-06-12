package com.jayantkrish.jklol.cvsm;

import com.jayantkrish.jklol.cvsm.lrt.LowRankTensor;
import com.jayantkrish.jklol.models.parametric.ParametricFamily;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.Tensor;

public interface LrtFamily extends ParametricFamily<LowRankTensor> {

  public int[] getDimensionNumbers();

  public int[] getDimensionSizes();

  public void setInitialTensor(Tensor tensor);

  public void increment(SufficientStatistics gradient,
      LowRankTensor currentValue, LowRankTensor increment, double multiplier);
}
