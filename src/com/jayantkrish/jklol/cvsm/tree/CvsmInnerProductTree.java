package com.jayantkrish.jklol.cvsm.tree;

import java.util.Arrays;
import java.util.List;
import java.util.SortedSet;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.cvsm.CvsmGradient;
import com.jayantkrish.jklol.cvsm.lrt.LowRankTensor;
import com.jayantkrish.jklol.cvsm.lrt.LowRankTensors;

public class CvsmInnerProductTree extends AbstractCvsmTree {
  
  private final CvsmTree bigTree;
  private final CvsmTree smallTree;
  
  public CvsmInnerProductTree(CvsmTree bigTree, CvsmTree smallTree) {
    super(bigTree.getValue().innerProduct(smallTree.getValue()));
    
    this.bigTree = bigTree;
    this.smallTree = smallTree;
  }
  
  @Override
  public List<CvsmTree> getSubtrees() {
    return Arrays.asList(bigTree, smallTree);
  }

  @Override
  public CvsmTree replaceSubtrees(List<CvsmTree> subtrees) {
    Preconditions.checkArgument(subtrees.size() == 2);
    return new CvsmInnerProductTree(subtrees.get(0), subtrees.get(1));
  }

  @Override
  public void backpropagateGradient(LowRankTensor treeGradient, CvsmGradient gradient) {
    LowRankTensor bigTreeValue = bigTree.getValue();
    LowRankTensor smallTreeValue = smallTree.getValue();
    
    SortedSet<Integer> expectedDims = Sets.newTreeSet(Ints.asList(bigTreeValue.getDimensionNumbers()));
    expectedDims.removeAll(Ints.asList(smallTreeValue.getDimensionNumbers()));
    Preconditions.checkArgument(Arrays.equals(treeGradient.getDimensionNumbers(), 
        Ints.toArray(expectedDims)));
    
    LowRankTensor smallTreeGradient = bigTreeValue.innerProduct(treeGradient);
    smallTree.backpropagateGradient(smallTreeGradient, gradient);

    LowRankTensor bigTreeGradient = LowRankTensors.outerProduct(smallTreeValue, treeGradient);
    bigTree.backpropagateGradient(bigTreeGradient, gradient);
  }

  @Override
  public double getLoss() {
    return 0;
  }
}
