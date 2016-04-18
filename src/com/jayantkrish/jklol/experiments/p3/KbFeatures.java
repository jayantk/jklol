package com.jayantkrish.jklol.experiments.p3;

import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.tensor.Tensor;

public class KbFeatures {

  private final List<String> predicateNames;
  private final List<Tensor> features;
  
  public KbFeatures(List<String> predicateNames, List<Tensor> features) {
    this.predicateNames = Preconditions.checkNotNull(predicateNames);
    this.features = Preconditions.checkNotNull(features);
    Preconditions.checkArgument(features.size() == predicateNames.size());
  }
  
  public List<String> getPredicateNames() {
    return predicateNames;
  }
  
  public List<Tensor> getFeatures() {
    return features;
  }
}
