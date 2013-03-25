package com.jayantkrish.jklol.dtree;

import junit.framework.TestCase;

import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.tensor.Tensor;

public class RegressionTreeTrainerTest extends TestCase {
  
  Tensor data, xorData, emptySplitData;
  Tensor targets, xorTargets, emptySplitTargets;
  
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
  
  private static final int[] xorFeatures = new int[] {
      0, 0,
      0, 1,
      1, 0,
      1, 1,
  };
  
  private static final double[] xorValues = new double[] {
    0, 1, 1, 0
  };
  
  public void setUp() {
    data = buildData(features);
    targets = new DenseTensor(new int[] {0}, new int[] {values.length}, values);
    
    xorData = buildData(xorFeatures);
    xorTargets = new DenseTensor(new int[] {0}, new int[] {xorValues.length}, xorValues);
    
    emptySplitData = DenseTensor.constant(new int[] {0, 1}, new int[] {2, 2}, 1.0);
    emptySplitTargets = new DenseTensor(new int[] {0}, new int[] {2}, new double[] {3.0, 4.0});
  }
  
  private static Tensor buildData(int[] features) {
    int numDataPoints = features.length / 2;
    int[] dims = new int[] {0, 1};
    int[] dimSizes = new int[] {numDataPoints, 2};
    
    DenseTensorBuilder builder = new DenseTensorBuilder(dims, dimSizes);
    for (int i = 0; i < numDataPoints; i++) {
      for (int j = 0; j < 2; j++) {
        builder.put(new int[] {i, j}, features[(i * 2) + j]);
      }
    }
    
    return builder.build();
  }
  
  public void testTrain() {
    RegressionTreeTrainer trainer = new RegressionTreeTrainer(1);
    RegressionTree tree = trainer.train(data, targets);
    
    System.out.println(tree);
    
    assertFalse(tree.isLeaf());
    assertEquals(0, tree.getFeature());
    assertEquals(0.5, tree.getSplitPoint());
    
    assertEquals(1.0, tree.getLowerTree().getLeafValue());
    assertEquals(6.0, tree.getHigherTree().getLeafValue());
  }

  public void testTrainXor() {
    RegressionTreeTrainer trainer = new RegressionTreeTrainer(2);
    RegressionTree tree = trainer.train(xorData, xorTargets);
    
    System.out.println(tree);
    
    assertFalse(tree.isLeaf());
    RegressionTree lower = tree.getLowerTree();
    RegressionTree higher = tree.getHigherTree();
    
    assertFalse(lower.isLeaf());
    assertEquals(0.0, lower.getLowerTree().getLeafValue());
    assertEquals(1.0, lower.getHigherTree().getLeafValue());

    assertFalse(higher.isLeaf());
    assertEquals(1.0, higher.getLowerTree().getLeafValue());
    assertEquals(0.0, higher.getHigherTree().getLeafValue());
  }
  
  public void testTrainEmptySplit() {
    RegressionTreeTrainer trainer = new RegressionTreeTrainer(2);
    RegressionTree tree = trainer.train(emptySplitData, emptySplitTargets);
    
    System.out.println(tree);
    
    assertTrue(tree.isLeaf());
    assertEquals(3.5, tree.getLeafValue());
  }
}
