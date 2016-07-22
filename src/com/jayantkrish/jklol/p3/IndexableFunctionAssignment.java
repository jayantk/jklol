package com.jayantkrish.jklol.p3;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
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

  private final Tensor elementFeatures;

  private IndexableFunctionAssignment(VariableNumMap inputVars, DiscreteVariable outputVar,
      int outputVarUnassigned, Tensor sparsity, int sparsityValueIndex, int[] values, Tensor elementFeatures) {
    this.inputVars = Preconditions.checkNotNull(inputVars);
    this.outputVar = Preconditions.checkNotNull(outputVar);
    this.outputVarUnassigned = outputVarUnassigned;
    this.sparsity = Preconditions.checkNotNull(sparsity);
    this.sparsityValueIndex = sparsityValueIndex;
    this.values = values;
    this.elementFeatures = elementFeatures;
  }

  public static IndexableFunctionAssignment unassignedDense(VariableNumMap inputVars,
      DiscreteVariable outputVar, Object unassignedValue, Tensor features) {
    Tensor sparsity = DenseTensor.constant(inputVars.getVariableNumsArray(), inputVars.getVariableSizes(), 1.0);
    int[] values = new int[sparsity.size()];
    int unassignedValueIndex = outputVar.getValueIndex(unassignedValue);
    Arrays.fill(values, unassignedValueIndex);

    return new IndexableFunctionAssignment(inputVars, outputVar,
        unassignedValueIndex, sparsity, -1, values, features);  
  }

  private final int getIndex(List<Object> args) {
    long keyNum = sparsity.dimKeyToKeyNum(inputVars.outcomeToIntArray(args));
    int index = sparsity.keyNumToIndex(keyNum);
    if (index != -1) {
      return index;
    }
    return -1;
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
    
    int valueIndex = outputVar.getValueIndex(value);
    
    int[] newValues = Arrays.copyOf(values, values.length);
    newValues[keyIndex] = valueIndex;
    
    return new IndexableFunctionAssignment(inputVars, outputVar, outputVarUnassigned,
        sparsity, sparsityValueIndex, newValues, elementFeatures);
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
    // TODO: precompute and cache this.
    int[] featureDims = elementFeatures.getDimensionNumbers();
    int[] featureSizes = elementFeatures.getDimensionSizes();
    int[] indicatorDims = Arrays.copyOf(featureDims, featureDims.length - 1);
    int[] indicatorSizes = Arrays.copyOf(featureSizes, featureSizes.length - 1);
    SparseTensorBuilder assignment = new SparseTensorBuilder(indicatorDims, indicatorSizes);
    
    for (int i = 0; i < sparsity.size(); i++) {
      int[] dimKey = sparsity.keyNumToDimKey(sparsity.indexToKeyNum(i));
      int[] newDimKey = Arrays.copyOf(dimKey, dimKey.length + 1);
      newDimKey[dimKey.length] = values[i];
      assignment.put(newDimKey, 1.0);
    }

    return elementFeatures.innerProduct(assignment.buildNoCopy());
  }
}
