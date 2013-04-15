package com.jayantkrish.jklol.cvsm;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.parametric.ListSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.ParametricFamily;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.util.IndexedList;

public class CvsmFamily implements ParametricFamily<Cvsm> {
  private static final long serialVersionUID = 1L;
  
  private final IndexedList<String> valueNames;
  private final List<LrtFamily> families;
  
  public CvsmFamily(IndexedList<String> valueNames, List<LrtFamily> families) {
    this.valueNames = Preconditions.checkNotNull(valueNames);
    this.families = Preconditions.checkNotNull(families);
    Preconditions.checkArgument(valueNames.size() == families.size());
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    List<SufficientStatistics> parameters = Lists.newArrayList();
    for (int i = 0; i < families.size(); i++) {
      parameters.add(families.get(i).getNewSufficientStatistics());
    }
    return new ListSufficientStatistics(valueNames.items(), parameters);
  }

  @Override
  public Cvsm getModelFromParameters(SufficientStatistics parameters) {
    List<LowRankTensor> tensors = Lists.newArrayList();
    List<SufficientStatistics> parameterList = parameters.coerceToList().getStatistics();
    Preconditions.checkArgument(parameterList.size() == families.size());
    for (int i = 0; i < families.size(); i++) {
      tensors.add(families.get(i).getModelFromParameters(parameterList.get(i)));
    }
       
    return new Cvsm(valueNames, tensors);
  }
  
  public void incrementValueSufficientStatistics(String valueName, LowRankTensor currentValue, 
      LowRankTensor valueGradient, SufficientStatistics gradient, double multiplier) {
    int familyIndex = valueNames.getIndex(valueName);
    SufficientStatistics gradientTerm = gradient.coerceToList().getStatisticByName(valueName);
    families.get(familyIndex).increment(gradientTerm, currentValue, valueGradient, multiplier);
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
