package com.jayantkrish.jklol.cvsm;

import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.cvsm.lrt.LowRankTensor;
import com.jayantkrish.jklol.cvsm.lrt.TensorLowRankTensor;
import com.jayantkrish.jklol.cvsm.tree.CvsmSquareLossTree;
import com.jayantkrish.jklol.cvsm.tree.CvsmTree;
import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.IndexedList;

public class CvsmTest extends TestCase {

  private Cvsm cvsm;
  private ExpressionParser<Expression2> exp;
  
  private static final String[] vectorNames = {
    "vec:block"
  };
  
  private static final double[][] vectorValues = {
    {1, 0, 4}
  };

  public void setUp() {
    IndexedList<String> tensorNames = IndexedList.create();
    tensorNames.addAll(Arrays.asList(vectorNames));
    List<LowRankTensor> tensors = Lists.newArrayList();
    for (int i = 0; i < vectorValues.length; i++) {
      tensors.add(new TensorLowRankTensor(new DenseTensor(new int[] {0}, new int[] {3}, vectorValues[i])));
    }
    cvsm = Cvsm.fromTensors(tensorNames, tensors);
    exp = ExpressionParser.expression2();
  }
  
  public void testVector() {
    CvsmTree tree = cvsm.getInterpretationTree(exp.parse("vec:block"));
    
    Tensor value = tree.getValue().getTensor();
    assertTrue(Arrays.equals(value.getValues(), vectorValues[0]));
    assertEquals(0.0, tree.getLoss());    
  }
  
  public void testSquareLoss() {
    CvsmTree tree = cvsm.getInterpretationTree(exp.parse("vec:block"));
    Tensor targets = new DenseTensor(new int[] {0}, new int[] {3}, new double[] {0, 2, -1});
    tree = new CvsmSquareLossTree(targets, tree);
    
    assertTrue(Arrays.equals(tree.getValue().getTensor().getValues(), vectorValues[0]));
    assertEquals(30.0, tree.getLoss());
  }
}
