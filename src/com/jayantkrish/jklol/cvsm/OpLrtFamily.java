package com.jayantkrish.jklol.cvsm;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.cvsm.lrt.LowRankTensor;
import com.jayantkrish.jklol.cvsm.lrt.LowRankTensors;
import com.jayantkrish.jklol.cvsm.lrt.OuterProductLowRankTensor;
import com.jayantkrish.jklol.cvsm.lrt.SumLowRankTensor;
import com.jayantkrish.jklol.cvsm.lrt.TensorLowRankTensor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.ListSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.models.parametric.TensorSufficientStatistics;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.Tensor;

public class OpLrtFamily implements LrtFamily {
  private static final long serialVersionUID = 1L;
  
  private final VariableNumMap vars;
  private final int rank;
  
  private final VariableNumMap smallestVar; 

    // If non-null, this tensor is added to the diagonal
    // entries of the matrix, making regularization apply
    // to the deviation from this value.
  private Tensor initialTensor;
  
  public OpLrtFamily(VariableNumMap vars, int rank) {
    this.vars = Preconditions.checkNotNull(vars);
    Preconditions.checkArgument(rank >= 0);
    this.rank = rank;
    
    int[] dimSizes = vars.getVariableSizes();
    int minValue = Ints.min(dimSizes);
    int minIndex = -1;
    for (int i = 0; i < dimSizes.length; i++) {
      if (dimSizes[i] == minValue) {
        minIndex = i;
        break;
      }
    }
    this.smallestVar = vars.intersection(minIndex);

    this.initialTensor = null;
  }

  @Override
  public int[] getDimensionNumbers() {
    return vars.getVariableNumsArray();
  }

    @Override
    public int[] getDimensionSizes() {
	return vars.getVariableSizes();
    }

    @Override
    public void setInitialTensor(Tensor tensor) {
	Preconditions.checkArgument(Arrays.equals(tensor.getDimensionNumbers(), getDimensionNumbers()));
	this.initialTensor = tensor;
    }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    List<SufficientStatistics> opStats = Lists.newArrayList();
    List<String> names = Lists.newArrayList();
    int[] varNums = vars.getVariableNumsArray();
    List<DiscreteVariable> varTypes = vars.getDiscreteVariables();
    for (int i = 0; i < rank; i++) {
      List<SufficientStatistics> curStats = Lists.newArrayList();
      for (int j = 0; j < varNums.length; j++) {
        DenseTensorBuilder builder = new DenseTensorBuilder(new int[] {varNums[j]}, 
            new int[] {varTypes.get(j).numValues()});
        curStats.add(new TensorSufficientStatistics(vars.intersection(varNums[j]), builder));
      }
      opStats.add(new ListSufficientStatistics(vars.getVariableNames(), curStats));
      names.add(Integer.toString(i));
    }

    DenseTensorBuilder builder = new DenseTensorBuilder(smallestVar.getVariableNumsArray(), 
            smallestVar.getVariableSizes());
    opStats.add(new TensorSufficientStatistics(smallestVar, builder));
    names.add("diag");

    return new ListSufficientStatistics(names, opStats);
  }

  @Override
  public LowRankTensor getModelFromParameters(SufficientStatistics parameters) {
    List<SufficientStatistics> opStats = parameters.coerceToList().getStatistics();
    Preconditions.checkArgument(opStats.size() == rank + 1); 
    LowRankTensor[] elements = new LowRankTensor[rank + 1]; 
    for (int j = 0; j < rank; j++) {
      List<SufficientStatistics> tensorStats = opStats.get(j).coerceToList().getStatistics();
      List<LowRankTensor> lrts = Lists.newArrayList();
      for (SufficientStatistics tensorStat : tensorStats) {
        lrts.add(new TensorLowRankTensor(((TensorSufficientStatistics) tensorStat).get()));
      }
      
      LowRankTensor result = lrts.get(0);
      for (int i = 1; i < lrts.size(); i++) {
        result = LowRankTensors.outerProduct(result, lrts.get(i));
      }

      elements[j] = result;
    }

    double[] diagValues = ((TensorSufficientStatistics) opStats.get(rank)).get().getValues();
    Tensor diagTensor = SparseTensor.diagonal(vars.getVariableNumsArray(), 
					      vars.getVariableSizes(), diagValues);
    if (initialTensor != null) {
	diagTensor = diagTensor.elementwiseAddition(initialTensor);
    }
    elements[rank] = new TensorLowRankTensor(diagTensor);

    LowRankTensor finalResult = SumLowRankTensor.create(elements);
    return finalResult; 
  }

  @Override 
  public void increment(SufficientStatistics gradient, LowRankTensor currentValue,
      LowRankTensor increment, double multiplier) {
    List<SufficientStatistics> opStats = gradient.coerceToList().getStatistics();
    LowRankTensor[] values = ((SumLowRankTensor) currentValue).getTerms();
    Preconditions.checkArgument(opStats.size() == rank + 1);
    Preconditions.checkArgument(values.length == rank + 1);
    
    for (int i = 0; i < rank; i++) {
      List<SufficientStatistics> tensorStats = opStats.get(i).coerceToList().getStatistics();
      Preconditions.checkArgument(tensorStats.size() == vars.size());
      List<LowRankTensor> terms = Lists.newArrayList();
      getOuterProductTerms(values[i], terms);
      
      for (int j = 0; j < tensorStats.size(); j++) {
        LowRankTensor currentIncrement = increment;
        for (int k = 0; k < tensorStats.size(); k++) {
          if (j == k) { continue; }
          currentIncrement = currentIncrement.innerProduct(terms.get(k));
        }
        TensorSufficientStatistics currentGradient = (TensorSufficientStatistics) tensorStats.get(j);
        currentGradient.increment(currentIncrement.getTensor(), multiplier);
      }
    }
    
    TensorSufficientStatistics diagParams = (TensorSufficientStatistics) opStats.get(rank);
    DenseTensorBuilder incrementBuilder = new DenseTensorBuilder(smallestVar.getVariableNumsArray(),
        smallestVar.getVariableSizes());
    int[] key = new int[currentValue.getDimensionNumbers().length];
    for (int i = 0; i < incrementBuilder.size(); i++) {
      Arrays.fill(key, i);
      double value = increment.getByDimKey(key);
      incrementBuilder.putByKeyNum(i, value);
    }
    diagParams.increment(incrementBuilder.buildNoCopy(), multiplier);
  }

  private void getOuterProductTerms(LowRankTensor tensor, List<LowRankTensor> terms) {
    if (tensor instanceof OuterProductLowRankTensor) {
      OuterProductLowRankTensor op = (OuterProductLowRankTensor) tensor;
      getOuterProductTerms(op.getLeft(), terms);
      terms.add(op.getRight());
    } else {
      terms.add(tensor);
    }
  }
  
  @Override
  public String getParameterDescription(SufficientStatistics parameters) {
    return getParameterDescription(parameters, -1);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) {
    return parameters.getDescription();
  }
}
