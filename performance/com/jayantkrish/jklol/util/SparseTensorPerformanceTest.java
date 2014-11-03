package com.jayantkrish.jklol.util;

import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.testing.PerformanceTestRunner;

public class SparseTensorPerformanceTest extends TensorPerformanceTest {

  public SparseTensorPerformanceTest() {
    super(SparseTensorBuilder.getFactory());
  }
    
  public static void main(String[] args) {
    PerformanceTestRunner.run(new SparseTensorPerformanceTest());
  }
}
