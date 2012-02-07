package com.jayantkrish.jklol.preprocessing;

import com.google.common.base.Function;
import com.jayantkrish.jklol.tensor.Tensor;

/**
 * Maps objects of type {@code T} to a vector of features (represented as a
 * {@code Tensor}), suitable for use in a classifier. Each input object is
 * mapped to a vector of the same dimensionality.
 * 
 * @author jayantk
 * @param <T>
 */
public interface FeatureVectorGenerator<T> extends Function<T, Tensor> {

  @Override
  public Tensor apply(T item);

  /**
   * Gets the number of features in the feature vectors returned by {@code this}
   * 
   * @return
   */
  public int getNumberOfFeatures();
}
