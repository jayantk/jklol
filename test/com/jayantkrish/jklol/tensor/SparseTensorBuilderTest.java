package com.jayantkrish.jklol.tensor;

/**
 * Unit tests for {@link SparseTensorBuilder}. The actual test cases are in the
 * superclass, {@link TensorBuilderTest}.
 * 
 * @author jayantk
 */
public class SparseTensorBuilderTest extends TensorBuilderTest {

  public SparseTensorBuilderTest() {
    super(new TensorFactory() {
      @Override
      public TensorBuilder getBuilder(int[] dimNums, int[] dimSizes) {
        return new SparseTensorBuilder(dimNums, dimSizes);
      }
    });
  }
}
