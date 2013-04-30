package com.jayantkrish.jklol.cvsm.tree;

import com.jayantkrish.jklol.cvsm.CvsmGradient;
import com.jayantkrish.jklol.cvsm.lrt.LowRankTensor;

public interface CvsmTree {

  LowRankTensor getValue();

  void backpropagateGradient(LowRankTensor treeGradient, CvsmGradient gradient);

  double getLoss();
}
