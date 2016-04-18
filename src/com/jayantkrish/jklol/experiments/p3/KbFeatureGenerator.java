package com.jayantkrish.jklol.experiments.p3;

import java.io.Serializable;
import java.util.List;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.IndexedList;

public class KbFeatureGenerator implements Serializable {
  private static final long serialVersionUID = 1L;

  public KbFeatures apply(KbState state) {
    KbEnvironment env = state.getEnvironment();
    
    double[][] entityFeatures = env.getCategoryFeatures();
    IndexedList<String> categoryNames = state.getCategories();
    int[][] categoryAssignment = state.getCategoryAssignment();
    int numFeatures = entityFeatures[0].length;
    int numEntities = state.getEnvironment().getEntities().size();
    List<String> predicateNames = Lists.newArrayList();
    List<Tensor> features = Lists.newArrayList();
    for (int i = 0; i < categoryAssignment.length; i++) {
      if (categoryAssignment[i] != null) {
        // Sum up the feature vectors for all of the true entities. 
        double[] featureVector = new double[numFeatures];
        for (int j = 0; j < numEntities; j++) {
          if (categoryAssignment[i][j] == KbState.TRUE_INT) {
            for (int k = 0; k < numFeatures; k++) {
              featureVector[k] += entityFeatures[j][k];
            }
          }
        }
        predicateNames.add(categoryNames.get(i));
        features.add(new DenseTensor(new int[] {0}, new int[] {numFeatures}, featureVector));
      }
    }
    
    double[][] entityPairFeatures = env.getRelationFeatures();
    IndexedList<String> relationNames = state.getRelations();
    int[][] relationAssignment = state.getRelationAssignment();
    numFeatures = entityPairFeatures[0].length;
    int numEntityPairs = state.getEnvironment().getEntities().size()
        * state.getEnvironment().getEntities().size();
    for (int i = 0; i < relationAssignment.length; i++) {
      if (relationAssignment[i] != null) {
        // Sum up the feature vectors for all of the true entities. 
        double[] featureVector = new double[numFeatures];
        for (int j = 0; j < numEntityPairs; j++) {
          if (relationAssignment[i][j] == KbState.TRUE_INT) {
            for (int k = 0; k < numFeatures; k++) {
              featureVector[k] += entityPairFeatures[j][k];
            }
          }
        }
        predicateNames.add(relationNames.get(i));
        features.add(new DenseTensor(new int[] {0}, new int[] {numFeatures}, featureVector));
      }
    }

    return new KbFeatures(predicateNames, features);
  }
}
