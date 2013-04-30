package com.jayantkrish.jklol.cvsm.tree;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.cvsm.CvsmGradient;
import com.jayantkrish.jklol.cvsm.lrt.LowRankTensor;
import com.jayantkrish.jklol.cvsm.lrt.LowRankTensors;
import com.jayantkrish.jklol.cvsm.lrt.TensorLowRankTensor;

public class CvsmSplitTree extends AbstractCvsmTree {
  
  private final CvsmTree subtree;
  
  private final CvsmTree evaluatedTree;
  private final String bindingName;

  public CvsmSplitTree(CvsmTree subtree, CvsmTree evaluatedTree, String bindingName) {
    super(subtree.getValue());
    this.subtree = Preconditions.checkNotNull(subtree);
    this.evaluatedTree = Preconditions.checkNotNull(evaluatedTree);
    this.bindingName = Preconditions.checkNotNull(bindingName);
  }

  @Override
  public List<CvsmTree> getSubtrees() {
    return Arrays.asList(subtree);
  }

  @Override
  public CvsmTree replaceSubtrees(List<CvsmTree> subtrees) {
    Preconditions.checkArgument(subtrees.size() == 1);
    return new CvsmSplitTree(subtrees.get(0), evaluatedTree, bindingName);
  }

  @Override
  public void backpropagateGradient(LowRankTensor treeGradient, CvsmGradient gradient) {
    LowRankTensor evaluatedValue = evaluatedTree.getValue();
    CvsmGradient evaluatedGradient = new CvsmGradient();
    evaluatedTree.backpropagateGradient(TensorLowRankTensor.zero(
        evaluatedValue.getDimensionNumbers(), evaluatedValue.getDimensionSizes()), evaluatedGradient);
    
    LowRankTensor gradientToBackpropagate = treeGradient;
    List<String> tensorNames = evaluatedGradient.getTensorNames();
    List<LowRankTensor> tensors = evaluatedGradient.getTensors();
    for (int i = 0; i < tensorNames.size(); i++) {
      String name = tensorNames.get(i);
      LowRankTensor value = tensors.get(i);
      if (name.equals(bindingName)) {
        gradientToBackpropagate = LowRankTensors.elementwiseAddition(gradientToBackpropagate, value);
      } else {
        gradient.incrementValue(name, value);
      }
    }
  }

  @Override
  public double getLoss() {
    return subtree.getLoss() + evaluatedTree.getLoss();
  }
}
