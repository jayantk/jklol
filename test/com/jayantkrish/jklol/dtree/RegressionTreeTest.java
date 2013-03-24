package com.jayantkrish.jklol.dtree;

import junit.framework.TestCase;

import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.tensor.Tensor;

public class RegressionTreeTest extends TestCase {

  RegressionTree tree1,tree2,tree3,tree4;
  
  Tensor vec1, vec2;
  
  public void setUp() {
    tree1 = RegressionTree.createLeaf(1.0);
    tree2 = RegressionTree.createLeaf(2.0);
    
    tree3 = RegressionTree.createSplit(1, 5.0, tree1, tree2);
    tree4 = RegressionTree.createSplit(2, 3.0, tree3, RegressionTree.createLeaf(4.0));
    
    vec1 = new DenseTensor(new int[] {0}, new int[] {3}, new double[] {2.0, 6.0, 2.0});
    vec2 = new DenseTensor(new int[] {0}, new int[] {3}, new double[] {2.0, 3.0, 4.0});
  }
  
  public void testClassify() {
    assertEquals(1.0, tree1.regress(vec1));
    assertEquals(2.0, tree2.regress(vec1));
    
    assertEquals(1.0, tree1.regress(vec2));
    assertEquals(2.0, tree2.regress(vec2));
    
    assertEquals(2.0, tree3.regress(vec1));
    assertEquals(1.0, tree3.regress(vec2));
    
    assertEquals(4.0, tree4.regress(vec2));
    assertEquals(2.0, tree4.regress(vec1));
  }
}
