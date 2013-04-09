package com.jayantkrish.jklol.cvsm;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.ListSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.ParametricFamily;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.models.parametric.TensorSufficientStatistics;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.IndexedList;

public class CvsmFamily implements ParametricFamily<Cvsm> {
  private static final long serialVersionUID = 1L;
  
  private final IndexedList<String> valueNames;
  private final List<VariableNumMap> valueVars;
  
  public CvsmFamily(IndexedList<String> valueNames, List<VariableNumMap> valueVars) {
    this.valueNames = Preconditions.checkNotNull(valueNames);
    this.valueVars = Preconditions.checkNotNull(valueVars);
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    List<SufficientStatistics> parameters = Lists.newArrayList();
    for (int i = 0; i < valueVars.size(); i++) {
      VariableNumMap curVars = valueVars.get(i);
      parameters.add(TensorSufficientStatistics.createDense(curVars, 
          new DenseTensorBuilder(curVars.getVariableNumsArray(), curVars.getVariableSizes())));
    }
    return new ListSufficientStatistics(valueNames.items(), parameters);
  }

  @Override
  public Cvsm getModelFromParameters(SufficientStatistics parameters) {
    List<Tensor> tensors = Lists.newArrayList();
    List<SufficientStatistics> parameterList = parameters.coerceToList().getStatistics();
    for (SufficientStatistics parameter : parameterList) {
      tensors.add(((TensorSufficientStatistics) parameter).get());
    }
       
    return new Cvsm(valueNames, tensors);
  }
  
  public void incrementValueSufficientStatistics(String valueName, Tensor valueGradient,
      SufficientStatistics gradient, double multiplier) {
    SufficientStatistics gradientTerm = gradient.coerceToList().getStatisticByName(valueName);
    ((TensorSufficientStatistics) gradientTerm).increment(valueGradient, multiplier);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters) {
    return getParameterDescription(parameters, -1);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) {
    List<SufficientStatistics> parameterList = parameters.coerceToList().getStatistics();
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < parameterList.size(); i++) {
      sb.append(valueNames.get(i));
      sb.append("\n");
      sb.append(parameterList.get(i).getDescription());
      sb.append("\n");
    }
    return sb.toString();
  }
}
