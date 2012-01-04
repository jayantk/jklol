package com.jayantkrish.jklol.tensor;

/**
 * Provides methods for retrieving {@link TensorBuilder}s. This interface
 * abstracts over different tensor implementations (specifically,
 * {@link DenseTensorBuilder} and {@link SparseTensorBuilder}).
 * 
 * @author jayantk
 */
public interface TensorFactory {

  /**
   * Gets a {@code TensorBuilder} which has the specified dimensions and size.
   * 
   * @param dimensionNums
   * @param dimensionSizes
   * @return
   */
  TensorBuilder getBuilder(int[] dimensionNums, int[] dimensionSizes);
 
}
