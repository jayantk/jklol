package com.jayantkrish.jklol.cvsm.tree;

import java.util.List;

import com.jayantkrish.jklol.cvsm.CvsmGradient;
import com.jayantkrish.jklol.cvsm.lrt.LowRankTensor;

public interface CvsmTree {

  LowRankTensor getValue();
  
  List<CvsmTree> getSubtrees();
  
  CvsmTree replaceSubtrees(List<CvsmTree> subtrees);

  void backpropagateGradient(LowRankTensor treeGradient, CvsmGradient gradient);

  double getLoss();
}
