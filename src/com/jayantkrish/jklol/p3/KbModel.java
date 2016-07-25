package com.jayantkrish.jklol.p3;

import java.io.Serializable;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.IndexedList;

public class KbModel implements Serializable {
  private static final long serialVersionUID = 2L;

  // Classifier weights for features that factorize
  // per predicate instance. Global features may
  // be included by creating a special "predicate"
  // to contain them.
  private final IndexedList<String> predicateNames;
  private final List<Tensor> eltClassifiers;
  private final List<Tensor> predClassifiers;
  
  public KbModel(IndexedList<String> predicateNames, List<Tensor> eltClassifiers,
      List<Tensor> predClassifiers) {
    this.predicateNames = Preconditions.checkNotNull(predicateNames);
    this.eltClassifiers = Preconditions.checkNotNull(eltClassifiers);
    this.predClassifiers = Preconditions.checkNotNull(predClassifiers);
  }

  public IndexedList<String> getPredicateNames() {
    return predicateNames;
  }

  public List<Tensor> getClassifiers() {
    return eltClassifiers;
  }

  public List<Tensor> getPredicateClassifiers() {
    return predClassifiers;
  }
}
