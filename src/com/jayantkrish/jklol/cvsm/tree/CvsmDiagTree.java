package com.jayantkrish.jklol.cvsm.tree;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.cvsm.CvsmGradient;
import com.jayantkrish.jklol.cvsm.lrt.LowRankTensor;
import com.jayantkrish.jklol.cvsm.lrt.TensorLowRankTensor;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.Tensor;

public class CvsmDiagTree extends AbstractCvsmTree {

  private final CvsmTree subtree;

  private final int[] subtreeTensorDims;
  private final int[] subtreeTensorSizes;
  
  public CvsmDiagTree(CvsmTree subtree) {
    super(new TensorLowRankTensor(SparseTensor.diagonal(new int[] {0, 1}, subtree.getValue().getTensor())));
    this.subtree = subtree;
    
    Tensor subtreeTensor = subtree.getValue().getTensor();
    this.subtreeTensorDims = subtreeTensor.getDimensionNumbers();
    this.subtreeTensorSizes = subtreeTensor.getDimensionSizes();
  }

  @Override
  public List<CvsmTree> getSubtrees() {
    return Arrays.asList(subtree);
  }

  @Override
  public CvsmTree replaceSubtrees(List<CvsmTree> subtrees) {
    Preconditions.checkArgument(subtrees.size() == 1);
    return new CvsmDiagTree(subtrees.get(0));
  }

  @Override
  public void backpropagateGradient(LowRankTensor treeGradient, CvsmGradient gradient) {
    Tensor gradientTensor = treeGradient.getTensor();

    DenseTensorBuilder builder = new DenseTensorBuilder(subtreeTensorDims, subtreeTensorSizes);
    int[] oldKey = new int[2];
    int[] newKey = new int[1];
    for (int i = 0; i < subtreeTensorSizes[0]; i++) {
      oldKey[0] = i;
      oldKey[1] = i;
      newKey[0] = i;
      
      builder.put(newKey, gradientTensor.getByDimKey(oldKey));
    }

    subtree.backpropagateGradient(new TensorLowRankTensor(builder.build()), gradient);
  }

  @Override
  public double getLoss() {
    return 0;
  }
}
