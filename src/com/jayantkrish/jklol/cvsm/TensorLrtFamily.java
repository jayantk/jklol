package com.jayantkrish.jklol.cvsm;

import java.util.Arrays;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.cvsm.lrt.LowRankTensor;
import com.jayantkrish.jklol.cvsm.lrt.TensorLowRankTensor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.models.parametric.TensorSufficientStatistics;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.tensor.Tensor;

/**
 * Returns full rank tensors defined over a set of variables.
 * 
 * @author jayantk
 */
public class TensorLrtFamily implements LrtFamily {
  private static final long serialVersionUID = 1L;
  
  private final VariableNumMap vars;
  
  // This tensor, if non-null, is added to the parameter values,
  // effectively changing regularization to apply to the deviation
  // away from this tensor.
  private Tensor initialTensor;
  
  public TensorLrtFamily(VariableNumMap vars) {
    this.vars = Preconditions.checkNotNull(vars);
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
    DenseTensorBuilder builder = new DenseTensorBuilder(vars.getVariableNumsArray(), 
        vars.getVariableSizes());
    return TensorSufficientStatistics.createDense(vars, builder);
  }

    private Tensor getTensorFromParameters(SufficientStatistics parameters) {
      Tensor tensor = ((TensorSufficientStatistics) parameters).get();
      if (initialTensor != null) {
	  tensor = tensor.elementwiseAddition(initialTensor);
      } 
      return tensor;
    }

  @Override
  public LowRankTensor getModelFromParameters(SufficientStatistics parameters) {
      return new TensorLowRankTensor(getTensorFromParameters(parameters));
  }

  @Override
  public LrtFamily rescaleFeatures(SufficientStatistics rescaling) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void increment(SufficientStatistics gradient, LowRankTensor value, 
      LowRankTensor increment, double multiplier) {
      ((TensorSufficientStatistics) gradient).increment(increment.getTensor(), multiplier);
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
