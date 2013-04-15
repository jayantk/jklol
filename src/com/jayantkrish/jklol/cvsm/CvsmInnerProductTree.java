package com.jayantkrish.jklol.cvsm;

import java.util.Arrays;
import java.util.SortedSet;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

public class CvsmInnerProductTree extends AbstractCvsmTree {
  
  private final CvsmTree bigTree;
  private final CvsmTree smallTree;
  
  public CvsmInnerProductTree(CvsmTree bigTree, CvsmTree smallTree) {
    super(bigTree.getValue().innerProduct(smallTree.getValue()));
    
    this.bigTree = bigTree;
    this.smallTree = smallTree;
  }

  @Override
  public void backpropagateGradient(LowRankTensor treeGradient,
      CvsmFamily family, SufficientStatistics gradient) {
    LowRankTensor bigTreeValue = bigTree.getValue();
    LowRankTensor smallTreeValue = smallTree.getValue();
    
    SortedSet<Integer> expectedDims = Sets.newTreeSet(Ints.asList(bigTreeValue.getDimensionNumbers()));
    expectedDims.removeAll(Ints.asList(smallTreeValue.getDimensionNumbers()));
    Preconditions.checkArgument(Arrays.equals(treeGradient.getDimensionNumbers(), 
        Ints.toArray(expectedDims)));
    
    LowRankTensor smallTreeGradient = bigTreeValue.innerProduct(treeGradient);
    smallTree.backpropagateGradient(smallTreeGradient, family, gradient);

    LowRankTensor bigTreeGradient = LowRankTensors.outerProduct(smallTreeValue, treeGradient);
    bigTree.backpropagateGradient(bigTreeGradient, family, gradient);
  }

  @Override
  public double getLoss() {
    return 0;
  }
}
