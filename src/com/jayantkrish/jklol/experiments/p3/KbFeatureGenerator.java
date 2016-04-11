package com.jayantkrish.jklol.experiments.p3;

import java.io.Serializable;
import java.util.Map;

import com.google.common.collect.Maps;
import com.jayantkrish.jklol.tensor.Tensor;

public class KbFeatureGenerator implements Serializable {
  private static final long serialVersionUID = 1L;

  public KbFeatures apply(KbState state) {
    KbEnvironment env = state.getEnvironment();
    
    Tensor entityFeatures = env.getCategoryFeatures().getWeights();
    Map<String, Tensor> predicateFeatures = Maps.newHashMap();
    for (String category : state.getCategories()) {
      Tensor f = state.getCategoryAssignment(category).getWeights();
      
      Tensor categoryFeatures = entityFeatures.innerProduct(f);
      predicateFeatures.put(category, categoryFeatures);
    }

    Tensor entityPairFeatures = env.getRelationFeatures().getWeights();
    for (String relation : state.getRelations()) {
      Tensor f = state.getRelationAssignment(relation).getWeights();
      
      Tensor relationFeatures = entityPairFeatures.innerProduct(f);
      predicateFeatures.put(relation, relationFeatures);
    }

    return new KbFeatures(predicateFeatures);
  }
}
