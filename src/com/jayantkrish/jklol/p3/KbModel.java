package com.jayantkrish.jklol.p3;

import java.io.Serializable;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.lisp.inc.ParametricContinuationIncEval.StateFeatures;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.IndexedList;

public class KbModel implements Serializable {
  private static final long serialVersionUID = 2L;

  // Classifier weights for features that factorize
  // per predicate instance (elt) and each predicate. 
  private final IndexedList<String> predicateNames;
  private final List<Tensor> eltClassifiers;
  private final List<Tensor> predClassifiers;
  
  // Classifier weights for actions.
  private final Tensor actionClassifier;
  private final FeatureVectorGenerator<StateFeatures> actionFeatureGen;

  public KbModel(IndexedList<String> predicateNames, List<Tensor> eltClassifiers,
      List<Tensor> predClassifiers, Tensor actionClassifier,
      FeatureVectorGenerator<StateFeatures> actionFeatureGen) {
    this.predicateNames = Preconditions.checkNotNull(predicateNames);
    this.eltClassifiers = Preconditions.checkNotNull(eltClassifiers);
    this.predClassifiers = Preconditions.checkNotNull(predClassifiers);
    this.actionClassifier = Preconditions.checkNotNull(actionClassifier);
    this.actionFeatureGen = Preconditions.checkNotNull(actionFeatureGen);
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
  
  public Tensor getActionClassifier() {
    return actionClassifier;
  }

  public Tensor generateActionFeatures(StateFeatures f) {
    return actionFeatureGen.apply(f);
  }
  
  public FeatureVectorGenerator<StateFeatures> getActionFeatureGenerator() {
    return actionFeatureGen;
  }
}
