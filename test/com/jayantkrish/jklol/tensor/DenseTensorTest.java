package com.jayantkrish.jklol.tensor;

/**
 * Unit tests for {@link DenseTensor}.
 * 
 * @author jayantk
 */
public class DenseTensorTest extends TensorTest {

  public DenseTensorTest() {
    super(new TensorFactory() {
      @Override
      public TensorBuilder getBuilder(int[] dimNums, int[] dimSizes) {
        return new DenseTensorBuilder(dimNums, dimSizes);
      }
    });
  }
}
