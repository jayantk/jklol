package com.jayantkrish.jklol.preprocessing;

import java.util.Arrays;

import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.Tensor;

/**
 * Always generates a feature vector containing a single 0.
 * 
 * @author jayantk
 *
 */
public class NullFeatureVectorGenerator<T> implements FeatureVectorGenerator<T> {
  private static final long serialVersionUID = 1L;

  private final DiscreteVariable featureDictionary;
  private final Tensor v;
  
  public NullFeatureVectorGenerator() {
    featureDictionary = new DiscreteVariable("nullFeatureGen", Arrays.asList("null"));
    v = SparseTensor.empty(new int[] {0}, new int[] {1});
  }

  @Override
  public Tensor apply(T item) {
    return v;
  }

  @Override
  public int getNumberOfFeatures() {
    return 1;
  }

  @Override
  public DiscreteVariable getFeatureDictionary() {
    return featureDictionary;
  }
}
