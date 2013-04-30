package com.jayantkrish.jklol.cvsm.tree;

import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.cvsm.Cvsm;
import com.jayantkrish.jklol.cvsm.CvsmFamily;
import com.jayantkrish.jklol.cvsm.lrt.LowRankTensor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.Tensor;

public class CvsmSplitTree extends AbstractCvsmTree {
  
  private final CvsmTree subtree;
  private final Expression splitExpression;

  public CvsmSplitTree(CvsmTree subtree) {
    super(subtree.getValue());
  }

  @Override
  public void backpropagateGradient(LowRankTensor treeGradient, CvsmFamily family, SufficientStatistics gradient) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public double getLoss(Cvsm environment) {
    
    
    Tensor value = getValue();
  }
}
