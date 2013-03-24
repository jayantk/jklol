package com.jayantkrish.jklol.dtree;

import junit.framework.TestCase;

import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.tensor.Tensor;

public class RegressionTreeTrainerTest extends TestCase {
  
  Tensor data;
  Tensor targets;
  
  private static final int[] features = new int[] {
      0, 0,
      0, 1,
      1, 0,
      1, 0,
      1, 1,
      1, 1,
  };
  
  private static final double[] values = new double[] {
      0.0, 2.0,
      5.0, 6.0, 6.0, 7.0,
  };
  
  public void setUp() {
    int numDataPoints = features.length / 2;
    int[] dims = new int[] {0, 1};
    int[] dimSizes = new int[] {numDataPoints, 2};
    
    DenseTensorBuilder builder = new DenseTensorBuilder(dims, dimSizes);
    for (int i = 0; i < numDataPoints; i++) {
      for (int j = 0; j < 2; j++) {
        builder.put(new int[] {i, j}, features[(i * 2) + j]);
      }
    }
    
    data = builder.build();
    targets = new DenseTensor(new int[] {0}, new int[] {numDataPoints}, values);
  }
  
  public void testTrain() {
    RegressionTreeTrainer trainer = new RegressionTreeTrainer();
    RegressionTree tree = trainer.train(data, targets);
    
    assertFalse(tree.isLeaf());
    assertEquals(0, tree.getFeature());
    assertEquals(0.5, tree.getSplitPoint());
    
    assertEquals(1.0, tree.getLowerTree().getLeafValue());
    assertEquals(6.0, tree.getHigherTree().getLeafValue());

    System.out.println(tree);
  }
}
