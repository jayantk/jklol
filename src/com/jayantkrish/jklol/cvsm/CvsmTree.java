package com.jayantkrish.jklol.cvsm;

import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

public interface CvsmTree {

  LowRankTensor getValue();

  void backpropagateGradient(LowRankTensor treeGradient, CvsmFamily family, SufficientStatistics gradient);
  
  double getLoss();
}
