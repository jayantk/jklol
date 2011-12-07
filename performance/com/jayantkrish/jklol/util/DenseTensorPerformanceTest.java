package com.jayantkrish.jklol.util;

import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.tensor.TensorBuilder;
import com.jayantkrish.jklol.tensor.TensorFactory;
import com.jayantkrish.jklol.testing.PerformanceTestRunner;

public class DenseTensorPerformanceTest extends TensorPerformanceTest {

  public DenseTensorPerformanceTest() {
    super(new TensorFactory() {
      @Override
      public TensorBuilder getBuilder(int[] dimNums, int[] dimSizes) {
        return new DenseTensorBuilder(dimNums, dimSizes);
      }
    });
  }
  
  public static void main(String[] args) {
    PerformanceTestRunner.run(new DenseTensorPerformanceTest());
  }
}
