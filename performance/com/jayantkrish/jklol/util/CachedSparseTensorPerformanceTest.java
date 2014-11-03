package com.jayantkrish.jklol.util;

import com.jayantkrish.jklol.tensor.CachedSparseTensor;
import com.jayantkrish.jklol.testing.PerformanceTestRunner;

public class CachedSparseTensorPerformanceTest extends TensorPerformanceTest {

  public CachedSparseTensorPerformanceTest() {
    super(CachedSparseTensor.getFactory());
  }

  public static void main(String[] args) {
    PerformanceTestRunner.run(new CachedSparseTensorPerformanceTest());
  }
}
