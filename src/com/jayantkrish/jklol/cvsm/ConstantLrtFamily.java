package com.jayantkrish.jklol.cvsm;

import java.util.Collections;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.cvsm.lrt.LowRankTensor;
import com.jayantkrish.jklol.cvsm.lrt.LrtFamily;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.ListSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.Tensor;

/**
 * Returns a constant tensor.
 * 
 * @author jayantk
 */
public class ConstantLrtFamily implements LrtFamily {
  private static final long serialVersionUID = 1L;
  
  private final VariableNumMap vars;
  private final LowRankTensor tensor;

  public ConstantLrtFamily(VariableNumMap vars, LowRankTensor tensor) {
    this.vars = Preconditions.checkNotNull(vars);
    this.tensor = Preconditions.checkNotNull(tensor);
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
    // This method could also just do nothing, but throwing an
    // exception seems like it will simplify debugging.
    throw new UnsupportedOperationException("Cannot set tensor of constant family.");
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
      return new ListSufficientStatistics(Collections.<String>emptyList(), Collections.<SufficientStatistics>emptyList());
  }

  @Override
  public LowRankTensor getModelFromParameters(SufficientStatistics parameters) {
      return tensor;
  }
  
  @Override
  public void increment(SufficientStatistics gradient, LowRankTensor value, 
      LowRankTensor increment, double multiplier) {
      // No need to do anything.
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters) {
      return "";
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) {
    return "";
  }
}
