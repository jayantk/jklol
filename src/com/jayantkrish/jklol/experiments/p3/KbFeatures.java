package com.jayantkrish.jklol.experiments.p3;

import java.util.Collection;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.tensor.Tensor;

public class KbFeatures {

  private final Map<String, Tensor> predicateFeatures;
  
  public KbFeatures(Map<String, Tensor> predicateFeatures) {
    this.predicateFeatures = Preconditions.checkNotNull(predicateFeatures);
  }
  
  public Collection<String> getPredicates() {
    return predicateFeatures.keySet();
  }
  
  public Tensor getFeatureVector(String predicate) {
    return predicateFeatures.get(predicate);
  }
}
