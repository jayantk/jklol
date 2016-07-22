package com.jayantkrish.jklol.experiments.p3;

import java.io.Serializable;
import java.util.List;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.p3.FunctionAssignment;
import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.IndexedList;

public class KbFeatureGenerator implements Serializable {
  private static final long serialVersionUID = 1L;

  private final boolean useGlobalPredicateFeatures;
  private final String globalFeaturePredicateName;
  
  private final int numCountFeatures;
  
  public KbFeatureGenerator(boolean useGlobalPredicateFeatures,
      String globalFeaturePredicateName, int numCountFeatures) {
    this.useGlobalPredicateFeatures = useGlobalPredicateFeatures;
    this.globalFeaturePredicateName = globalFeaturePredicateName;
    this.numCountFeatures = numCountFeatures;
  }

  public KbFeatures apply(KbState state) {
    IndexedList<String> functionNames = state.getFunctions();
    List<FunctionAssignment> functionAssignments = state.getAssignments();
    List<String> featurizedFunctionNames = Lists.newArrayList();
    List<Tensor> features = Lists.newArrayList();
    for (int i = 0; i < functionAssignments.size(); i++) {
      FunctionAssignment assignment = functionAssignments.get(i);
      if (assignment != null) {
        featurizedFunctionNames.add(functionNames.get(i));
        features.add(assignment.getFeatureVector());
      }
    }

    /*
    if (useGlobalPredicateFeatures) {
      Tensor globalFeatures = generateGlobalPredicateFeatures(state);
      featurizedFunctionNames.add(globalFeaturePredicateName);
      features.add(globalFeatures);
    }
    */

    return new KbFeatures(featurizedFunctionNames, features);
  }
  
  /*
  private Tensor generateGlobalPredicateFeatures(KbState state) {
    IndexedList<String> categoryNames = state.getCategories();
    int numFeatures = (categoryNames.size() + 1) * numCountFeatures;
    double[] featureVector = new double[numFeatures];

    int[][] categoryAssignment = state.getCategoryAssignment();
    int numEntities = state.getEnvironment().getEntities().size();
    for (int i = 0; i < categoryAssignment.length; i++) {
      if (categoryAssignment[i] != null) {
        // If every entity has been assigned, count the number of
        // entities that the predicate is true for.
        int count = 0;
        boolean complete = true;
        for (int j = 0; j < numEntities; j++) {
          if (categoryAssignment[i][j] == KbState.UNASSIGNED_INT) {
            complete = false;
            break;
          } else if (categoryAssignment[i][j] == KbState.TRUE_INT) {
            count++;
          }
        }
        
        if (complete) {
          int featureIndex = Math.min(count, numCountFeatures - 1);
          featureVector[i * numCountFeatures + featureIndex] = 1.0;
          featureVector[categoryNames.size() * numCountFeatures + featureIndex] += 1.0;
        }
      }
    }

    return new DenseTensor(new int[] {0}, new int[] {numFeatures}, featureVector);
  }
  */
  
  public List<String> getGlobalPredicateFeatureNames(List<String> categoryNames) {
    List<String> featureNames = Lists.newArrayList();
    for (int i = 0; i < categoryNames.size(); i++) {
      for (int j = 0; j < numCountFeatures - 1; j++) {
        featureNames.add(categoryNames.get(i) + "_size_=" + j);
      }
      featureNames.add(categoryNames.get(i) + "_size_>=" + (numCountFeatures - 1));
    }

    for (int j = 0; j < numCountFeatures - 1; j++) {
      featureNames.add("category_size_=" + j);
    }
    featureNames.add("category_size_>=" + (numCountFeatures - 1));

    return featureNames;
  }
}
