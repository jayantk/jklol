package com.jayantkrish.jklol.experiments.p3;

import java.util.List;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.p3.FunctionAssignment;
import com.jayantkrish.jklol.p3.IndexableFunctionAssignment;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.Tensor;

public class PredicateSizeFeatureVectorGenerator implements FeatureVectorGenerator<FunctionAssignment> {
  private static final long serialVersionUID = 1L;

  private final int maxCount;
  private final int valueInt;
  private final DiscreteVariable featureDictionary;

  public PredicateSizeFeatureVectorGenerator(int maxCount, int valueInt) {
    this.maxCount = maxCount;
    this.valueInt = valueInt;
    this.featureDictionary = generateFeatureDictionary(maxCount);
  }

  @Override
  public Tensor apply(FunctionAssignment item) {
    IndexableFunctionAssignment a = (IndexableFunctionAssignment) item;
    int[] values = a.getValueArray();

    if (!a.isComplete()) {
      return SparseTensor.empty(new int[] {0}, new int[] {maxCount + 1});
    }

    int count = 0;
    for (int i = 0; i < values.length; i++) {
      if (values[i] == valueInt) {
        count++;
      }
    }

    int featureIndex = Math.min(count, maxCount);
    return SparseTensor.singleElement(new int[] {0}, new int[] {maxCount + 1},
        new int[] {featureIndex}, 1.0);
  }

  @Override
  public int getNumberOfFeatures() {
    return maxCount + 1;
  }

  @Override
  public DiscreteVariable getFeatureDictionary() {
    return featureDictionary;
  }
  
  private static DiscreteVariable generateFeatureDictionary(int maxCount) {
    List<String> featureNames = Lists.newArrayList();
    for (int j = 0; j < maxCount; j++) {
      featureNames.add("size=" + j);
    }
    featureNames.add("size>=" + maxCount);
    return new DiscreteVariable("predicateSizeFeatures", featureNames);
  }
}
