package com.jayantkrish.jklol.cvsm;

import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.Tensor;

public interface CvsmTree {

  Tensor getValue();

  void backpropagateGradient(Tensor treeGradient, CvsmFamily family, SufficientStatistics gradient);
  
  double getLoss();
}
