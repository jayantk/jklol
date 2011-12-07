package com.jayantkrish.jklol.tensor;

/**
 * Unit tests for {@link DenseTensorBuilder}. Actual test cases are in the
 * superclass, {@link TensorBuilderTest}.
 * 
 * @author jayantk
 */
public class DenseTensorBuilderTest extends TensorBuilderTest {

  public DenseTensorBuilderTest() {
    super(new TensorFactory() {
      @Override
      public TensorBuilder getBuilder(int[] dimNums, int[] dimSizes) {
        return new DenseTensorBuilder(dimNums, dimSizes);
      }
    });
  }
}
