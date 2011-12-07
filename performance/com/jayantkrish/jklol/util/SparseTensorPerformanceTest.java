package com.jayantkrish.jklol.util;

import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.tensor.TensorBuilder;
import com.jayantkrish.jklol.tensor.TensorFactory;
import com.jayantkrish.jklol.testing.PerformanceTestRunner;

public class SparseTensorPerformanceTest extends TensorPerformanceTest {

  public SparseTensorPerformanceTest() {
    super(new TensorFactory() {
      @Override
      public TensorBuilder getBuilder(int[] dimNums, int[] dimSizes) {
        return new SparseTensorBuilder(dimNums, dimSizes);
      }
    });
  }
    
  public static void main(String[] args) {
    PerformanceTestRunner.run(new SparseTensorPerformanceTest());
  }
}
