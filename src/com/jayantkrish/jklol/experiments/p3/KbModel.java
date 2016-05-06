package com.jayantkrish.jklol.experiments.p3;

import java.io.Serializable;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.IndexedList;

public class KbModel implements Serializable {
  private static final long serialVersionUID = 1L;

  // Classifier weights for features that factorize
  // per predicate instance. Global features may
  // be included by creating a special "predicate"
  // to contain them.
  private final IndexedList<String> predicateNames;
  private final List<Tensor> classifiers;
  
  private final KbFeatureGenerator featureGenerator;
  
  public KbModel(IndexedList<String> predicateNames, List<Tensor> classifiers,
      KbFeatureGenerator featureGenerator) {
    this.predicateNames = Preconditions.checkNotNull(predicateNames);
    this.classifiers = Preconditions.checkNotNull(classifiers);
    this.featureGenerator = featureGenerator;
  }
  
  public IndexedList<String> getPredicateNames() {
    return predicateNames;
  }
  
  public List<Tensor> getClassifiers() {
    return classifiers;
  }
  
  public KbFeatureGenerator getFeatureGenerator() {
    return featureGenerator;
  }
}
