package com.jayantkrish.jklol.p3;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.tensor.Tensor;

public class IndexableFunctionAssignment implements FunctionAssignment {
  
  private final VariableNumMap inputVars;
  private final DiscreteVariable outputVar;

  // The index of the value in outputVar that represents
  // an unassigned element.
  private final int outputVarUnassigned;
  
  // This tensor determines which assignments to inputVars
  // can be assigned a value. All other assignments return
  // the value sparsityValueIndex.
  private final Tensor sparsity;
  private final int sparsityValueIndex;
  
  // The values assigned to the permissible assignments. Each
  // assignment's index in values is determined by the 
  // corresponding index in the sparsity tensor.
  private final int[] values;

  // A feature vector per input -> output mapping. 
  private final Tensor elementFeatures;

  // The current feature vector of this assignment.
  private final double[] cachedFeatureVector;

  private IndexableFunctionAssignment(VariableNumMap inputVars, DiscreteVariable outputVar,
      int outputVarUnassigned, Tensor sparsity, int sparsityValueIndex, int[] values,
      Tensor elementFeatures, double[] cachedFeatureVector) {
    this.inputVars = Preconditions.checkNotNull(inputVars);
    this.outputVar = Preconditions.checkNotNull(outputVar);
    this.outputVarUnassigned = outputVarUnassigned;
    this.sparsity = Preconditions.checkNotNull(sparsity);
    this.sparsityValueIndex = sparsityValueIndex;
    this.values = values;
    this.elementFeatures = elementFeatures;
    this.cachedFeatureVector = cachedFeatureVector;
  }

  public static IndexableFunctionAssignment unassignedDense(VariableNumMap inputVars,
      DiscreteVariable outputVar, Object unassignedValue, Tensor features) {
    int[] featureDims = features.getDimensionSizes();
    Preconditions.checkArgument(featureDims.length == inputVars.size() + 2,
        "Incorrect number of feature dimensions");
    Preconditions.checkArgument(featureDims[inputVars.size()] == outputVar.numValues(),
        "Incorrect ordering of feature dimensions");
    
    Tensor sparsity = DenseTensor.constant(inputVars.getVariableNumsArray(), inputVars.getVariableSizes(), 1.0);
    int[] values = new int[sparsity.size()];
    int unassignedValueIndex = outputVar.getValueIndex(unassignedValue);
    Arrays.fill(values, unassignedValueIndex);
    
    int[] featureDimSizes = features.getDimensionSizes();
    double[] cachedFeatureVector = new double[featureDimSizes[featureDimSizes.length - 1]];
    
    for (int i = 0; i < values.length; i++) {
      int[] dimKey = sparsity.keyNumToDimKey(sparsity.indexToKeyNum(i));
      updateFeatures(dimKey, values[i], features, inputVars.size(), cachedFeatureVector, 1.0);
    }

    return new IndexableFunctionAssignment(inputVars, outputVar,
        unassignedValueIndex, sparsity, -1, values, features, cachedFeatureVector);  
  }

  private final int getIndex(List<Object> args) {
    long keyNum = sparsity.dimKeyToKeyNum(inputVars.outcomeToIntArray(args));
    int index = sparsity.keyNumToIndex(keyNum);
    if (index != -1) {
      return index;
    }
    return -1;
  }

  public Tensor getElementFeatures() {
    return elementFeatures;
  }
  
  public int[] getValueArray() {
    return values;
  }
  
  public boolean isComplete() {
    for (int i = 0; i < values.length; i++) {
      if (values[i] == outputVarUnassigned) {
        return false;
      }
    }
    return true;
  }

  @Override
  public Object getValue(List<Object> args) {
    int index = getIndex(args);
    int valueIndex = -1;
    if (index == -1) {
      valueIndex = sparsityValueIndex;
    } else {
      valueIndex = values[index];
    }
    
    return outputVar.getValue(valueIndex);
  }

  @Override
  public IndexableFunctionAssignment putValue(List<Object> args, Object value) {
    int keyIndex = getIndex(args);
    Preconditions.checkArgument(keyIndex != -1,
        "Putting value \"%s\" not permitted by this FunctionAssignment.", args);
    
    int newValueIndex = outputVar.getValueIndex(value);
    
    int[] newValues = Arrays.copyOf(values, values.length);
    int oldValueIndex = newValues[keyIndex];
    newValues[keyIndex] = newValueIndex;
    
    double[] newFeatureVector = Arrays.copyOf(cachedFeatureVector, cachedFeatureVector.length);
    int[] dimKey = sparsity.keyNumToDimKey(sparsity.indexToKeyNum(keyIndex));
    updateFeatures(dimKey, oldValueIndex, elementFeatures, inputVars.size(), newFeatureVector, -1.0);
    updateFeatures(dimKey, newValueIndex, elementFeatures, inputVars.size(), newFeatureVector, 1.0);
    
    return new IndexableFunctionAssignment(inputVars, outputVar, outputVarUnassigned,
        sparsity, sparsityValueIndex, newValues, elementFeatures, newFeatureVector);
  }

  @Override
  public boolean isConsistentWith(FunctionAssignment other) {
    Preconditions.checkState(other instanceof IndexableFunctionAssignment);
    IndexableFunctionAssignment o = (IndexableFunctionAssignment) other;

    int[] otherValues = o.values;    
    Preconditions.checkState(values.length == otherValues.length);
    for (int i = 0; i < values.length; i++) {
      if (values[i] != otherValues[i] && values[i] != outputVarUnassigned
          && otherValues[i] != outputVarUnassigned) {
        return false;
      }
    }
    return true;
  }

  @Override
  public Tensor getFeatureVector() {
    return new DenseTensor(new int[] {0}, new int[] {cachedFeatureVector.length}, cachedFeatureVector);
  }

  private static void updateFeatures(int[] inputKey, int value, Tensor elementFeatures,
      int numInputVars, double[] featureVector, double multiplier) {
    long valueOffset = elementFeatures.getDimensionOffsets()[numInputVars];
    long first = elementFeatures.dimKeyPrefixToKeyNum(inputKey) + (valueOffset * value);
    long last = first + valueOffset;
    
    int index = elementFeatures.getNearestIndex(first);
    long cur = elementFeatures.indexToKeyNum(index);
    while (cur < last) {
      double featureValue = elementFeatures.getByIndex(index);
      featureVector[(int) (cur - first)] += featureValue * multiplier;

      // Advance the index
      index++;
      cur = elementFeatures.indexToKeyNum(index);
    }
  }
}
