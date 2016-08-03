package com.jayantkrish.jklol.p3;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.TableFactor;
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
  // Index of the first element in values whose value is 
  // unassigned.
  private final int firstUnassignedIndex;

  // A feature vector per input -> output mapping.
  private final DiscreteVariable featureVar;
  private final Tensor elementFeatures;

  // The current feature vector of this assignment.
  private final double[] cachedFeatureVector;

  protected IndexableFunctionAssignment(VariableNumMap inputVars, DiscreteVariable outputVar,
      int outputVarUnassigned, Tensor sparsity, int sparsityValueIndex, int[] values,
      int firstUnassignedIndex, DiscreteVariable featureVar, Tensor elementFeatures, 
      double[] cachedFeatureVector) {
    this.inputVars = Preconditions.checkNotNull(inputVars);
    this.outputVar = Preconditions.checkNotNull(outputVar);
    this.outputVarUnassigned = outputVarUnassigned;
    this.sparsity = Preconditions.checkNotNull(sparsity);
    this.sparsityValueIndex = sparsityValueIndex;
    this.values = values;
    this.firstUnassignedIndex = firstUnassignedIndex;
    this.featureVar = featureVar;
    this.elementFeatures = elementFeatures;
    this.cachedFeatureVector = cachedFeatureVector;
  }

  public static IndexableFunctionAssignment unassignedDense(VariableNumMap inputVars,
      DiscreteVariable outputVar, Object unassignedValue, DiscreteVariable featureVar,
      Tensor features) {
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
        unassignedValueIndex, sparsity, -1, values, 0, featureVar,
        features, cachedFeatureVector);  
  }

  public static IndexableFunctionAssignment unassignedSparse(VariableNumMap inputVars,
      DiscreteVariable outputVar, Object unassignedValue, Tensor sparsity,
      Object sparsityValue, DiscreteVariable featureVar, Tensor features) {
    int[] featureDims = features.getDimensionSizes();
    Preconditions.checkArgument(featureDims.length == inputVars.size() + 2,
        "Incorrect number of feature dimensions");
    Preconditions.checkArgument(featureDims[inputVars.size()] == outputVar.numValues(),
        "Incorrect ordering of feature dimensions");

    int[] values = new int[sparsity.size()];
    int unassignedValueIndex = outputVar.getValueIndex(unassignedValue);
    int sparsityValueIndex = outputVar.getValueIndex(sparsityValue);

    Arrays.fill(values, unassignedValueIndex);

    int[] featureDimSizes = features.getDimensionSizes();
    double[] cachedFeatureVector = new double[featureDimSizes[featureDimSizes.length - 1]];

    for (int i = 0; i < values.length; i++) {
      int[] dimKey = sparsity.keyNumToDimKey(sparsity.indexToKeyNum(i));
      updateFeatures(dimKey, values[i], features, inputVars.size(), cachedFeatureVector, 1.0);
    }

    return new IndexableFunctionAssignment(inputVars, outputVar,
        unassignedValueIndex, sparsity, sparsityValueIndex, values, 0, featureVar,
        features, cachedFeatureVector);
  }
  
  public VariableNumMap getInputVars() {
    return inputVars;
  }
  
  public DiscreteVariable getOutputVar() {
    return outputVar;
  }
  
  public int getUnassignedIndex() {
    return outputVarUnassigned;
  }

  public Tensor getSparsity() {
    return sparsity;
  }

  public DiscreteFactor getSparsityFactor() {
    return new TableFactor(inputVars, sparsity);
  }

  public Set<ImmutableList<Object>> getArgSet() {
    Set<ImmutableList<Object>> argSet = Sets.newHashSet();
    for (int i = 0; i < sparsity.size(); i++) {
      argSet.add(indexToArgs(i));
    }
    return argSet;
  }

  public int getSparsityIndex() {
    return sparsityValueIndex;
  }

  public int[] getValueArray() {
    return values;
  }

  public int getFirstUnassignedIndex() {
    return firstUnassignedIndex;
  }

  public DiscreteVariable getElementFeatureVar() {
    return featureVar;
  }

  public Tensor getElementFeatures() {
    return elementFeatures;
  }
  
  public DiscreteFactor getElementFeaturesFactor() {
    int[] dims = elementFeatures.getDimensionNumbers();
    int outputDim = dims[dims.length - 2];
    int featureDim = dims[dims.length - 1];
    
    VariableNumMap outputVarNumMap = VariableNumMap.singleton(outputDim,
        outputVar.getName(), outputVar);
    VariableNumMap featureVarNumMap = VariableNumMap.singleton(featureDim,
        featureVar.getName(), featureVar);
    
    VariableNumMap vars = VariableNumMap.unionAll(inputVars, outputVarNumMap, featureVarNumMap);
    return new TableFactor(vars, elementFeatures);
  }

  public boolean isComplete() {
    return firstUnassignedIndex == values.length;
  }

  public void indexToDimKey(int index, int[] toFill) {
    Preconditions.checkArgument(toFill.length == inputVars.size());
    sparsity.keyNumToDimKey(sparsity.indexToKeyNum(index), toFill);
  }

  public ImmutableList<Object> indexToArgs(int index) {
    long keyNum = sparsity.indexToKeyNum(index);
    int[] dimKey = sparsity.keyNumToDimKey(keyNum);
    return ImmutableList.copyOf(inputVars.intArrayToAssignment(dimKey).getValues());
  }

  public int argsToIndex(List<?> args) {
    long keyNum = sparsity.dimKeyToKeyNum(inputVars.outcomeToIntArray(args));
    int index = sparsity.keyNumToIndex(keyNum);
    if (index != -1) {
      return index;
    }
    return -1;
  }

  public int getValueIndex(int argIndex) {
    if (argIndex == -1) {
      return sparsityValueIndex;
    } else {
      return values[argIndex];
    }
  }

  @Override
  public Object getValue(List<?> args) {
    int index = argsToIndex(args);
    int valueIndex = -1;
    if (index == -1) {
      valueIndex = sparsityValueIndex;
    } else {
      valueIndex = values[index];
    }
    
    return outputVar.getValue(valueIndex);
  }

  @Override
  public IndexableFunctionAssignment putValue(List<?> args, Object value) {
    int keyIndex = argsToIndex(args);
    Preconditions.checkArgument(keyIndex != -1,
        "Putting value for args \"%s\" not permitted by this FunctionAssignment.", args);
    int newValueIndex = outputVar.getValueIndex(value);

    return put(keyIndex, newValueIndex);
  }
  
  public IndexableFunctionAssignment putAll(Tensor t, Object value) {
    Preconditions.checkArgument(Arrays.equals(t.getDimensionSizes(), sparsity.getDimensionSizes())); 
    
    int newValueIndex = outputVar.getValueIndex(value);
    int[] newValues = Arrays.copyOf(values, values.length);
    double[] newFeatureVector = Arrays.copyOf(cachedFeatureVector, cachedFeatureVector.length);
    int[] dimKey = new int[2];
    for (int i = 0; i < t.size(); i++) {
      long keyNum = t.indexToKeyNum(i);
      int myIndex = sparsity.keyNumToIndex(keyNum);
      sparsity.keyNumToDimKey(keyNum, dimKey);

      int oldValueIndex = newValues[myIndex];
      newValues[myIndex] = newValueIndex;
      updateFeatures(dimKey, oldValueIndex, elementFeatures, inputVars.size(), newFeatureVector, -1.0);
      updateFeatures(dimKey, newValueIndex, elementFeatures, inputVars.size(), newFeatureVector, 1.0);
    }
    
    int nextUnassignedIndex = firstUnassignedIndex;
    while (nextUnassignedIndex < newValues.length &&
        newValues[nextUnassignedIndex] != outputVarUnassigned) {
      nextUnassignedIndex++;
    }
    
    return new IndexableFunctionAssignment(inputVars, outputVar, outputVarUnassigned,
        sparsity, sparsityValueIndex, newValues, nextUnassignedIndex, featureVar,
        elementFeatures, newFeatureVector);
  }
  
  public IndexableFunctionAssignment put(int keyIndex, int newValueIndex) {
    Preconditions.checkArgument(keyIndex != -1);

    int[] newValues = Arrays.copyOf(values, values.length);
    int oldValueIndex = newValues[keyIndex];
    newValues[keyIndex] = newValueIndex;
    
    double[] newFeatureVector = Arrays.copyOf(cachedFeatureVector, cachedFeatureVector.length);
    int[] dimKey = sparsity.keyNumToDimKey(sparsity.indexToKeyNum(keyIndex));
    updateFeatures(dimKey, oldValueIndex, elementFeatures, inputVars.size(), newFeatureVector, -1.0);
    updateFeatures(dimKey, newValueIndex, elementFeatures, inputVars.size(), newFeatureVector, 1.0);
    
    int nextUnassignedIndex = firstUnassignedIndex;
    while (nextUnassignedIndex < newValues.length &&
        newValues[nextUnassignedIndex] != outputVarUnassigned) {
      nextUnassignedIndex++;
    }

    return new IndexableFunctionAssignment(inputVars, outputVar, outputVarUnassigned,
        sparsity, sparsityValueIndex, newValues, nextUnassignedIndex, featureVar,
        elementFeatures, newFeatureVector);
  }
  
  public IndexableFunctionAssignment putAll(int[] keyIndexes, int[] newValueIndexes) {
    int[] newValues = Arrays.copyOf(values, values.length);
    double[] newFeatureVector = Arrays.copyOf(cachedFeatureVector, cachedFeatureVector.length);
    for (int i = 0; i < keyIndexes.length; i++) {
      int keyIndex = keyIndexes[i];
      int newValueIndex = newValueIndexes[i];
      int oldValueIndex = newValues[keyIndex];
      newValues[keyIndex] = newValueIndex;
    
      int[] dimKey = sparsity.keyNumToDimKey(sparsity.indexToKeyNum(keyIndex));
      updateFeatures(dimKey, oldValueIndex, elementFeatures, inputVars.size(), newFeatureVector, -1.0);
      updateFeatures(dimKey, newValueIndex, elementFeatures, inputVars.size(), newFeatureVector, 1.0);
    }

    int nextUnassignedIndex = firstUnassignedIndex;
    while (nextUnassignedIndex < newValues.length &&
        newValues[nextUnassignedIndex] != outputVarUnassigned) {
      nextUnassignedIndex++;
    }

    return new IndexableFunctionAssignment(inputVars, outputVar, outputVarUnassigned,
        sparsity, sparsityValueIndex, newValues, nextUnassignedIndex, featureVar,
        elementFeatures, newFeatureVector);
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
  public boolean isEqualTo(FunctionAssignment other) {
    Preconditions.checkState(other instanceof IndexableFunctionAssignment);
    IndexableFunctionAssignment o = (IndexableFunctionAssignment) other;

    int[] otherValues = o.values;    
    Preconditions.checkState(values.length == otherValues.length);
    return Arrays.equals(values, otherValues);
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
    int lastIndex = elementFeatures.getNearestIndex(last);
    while (index < lastIndex) {
      long cur = elementFeatures.indexToKeyNum(index);
      double featureValue = elementFeatures.getByIndex(index);
      featureVector[(int) (cur - first)] += featureValue * multiplier;

      // Advance the index
      index++;
    }
  }
}
