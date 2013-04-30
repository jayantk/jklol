package com.jayantkrish.jklol.cvsm;

import com.jayantkrish.jklol.cvsm.lrt.LowRankTensor;
import com.jayantkrish.jklol.models.parametric.ParametricFamily;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

public interface LrtFamily extends ParametricFamily<LowRankTensor> {

  public int[] getDimensionNumbers();

  public void increment(SufficientStatistics gradient,
      LowRankTensor currentValue, LowRankTensor increment, double multiplier);
}
