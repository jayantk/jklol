package com.jayantkrish.jklol.tensor;

/**
 * Unit tests for {@link DenseTensorBuilder}. Actual test cases are in the
 * superclass, {@link TensorBuilderTest}.
 * 
 * @author jayantk
 */
public class DenseTensorBuilderTest extends TensorBuilderTest {

  public DenseTensorBuilderTest() {
    super(DenseTensorBuilder.getFactory());
  }
}
