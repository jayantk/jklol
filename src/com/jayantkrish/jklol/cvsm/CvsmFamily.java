package com.jayantkrish.jklol.cvsm;

import java.util.Collections;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.cvsm.Cvsm.LazyLowRankTensor;
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
    List<SufficientStatistics> parameters = Collections.<SufficientStatistics>nCopies(families.size(), null);
    return new CvsmSufficientStatistics(valueNames.items(), families, parameters);
  }

  @Override
  public Cvsm getModelFromParameters(SufficientStatistics parameters) {
    List<LazyLowRankTensor> tensors = Lists.newArrayList();
    CvsmSufficientStatistics cvsmStats = (CvsmSufficientStatistics) parameters;
    
    Preconditions.checkArgument(cvsmStats.size() == families.size());
    for (int i = 0; i < families.size(); i++) {
      tensors.add(new ParameterLazyLowRankTensor(families.get(i), cvsmStats, i));
    }

    return new Cvsm(valueNames, tensors);
  }

  public void incrementValueSufficientStatistics(String valueName, LowRankTensor currentValue, 
      LowRankTensor valueGradient, SufficientStatistics gradient, double multiplier) {
    int familyIndex = valueNames.getIndex(valueName);
    SufficientStatistics familyGradient = families.get(familyIndex).getNewSufficientStatistics();
    families.get(familyIndex).increment(familyGradient, currentValue, valueGradient, multiplier);
    
    CvsmSufficientStatistics cvsmStats = (CvsmSufficientStatistics) gradient;
    cvsmStats.incrementEntry(familyIndex, familyGradient);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters) {
    return getParameterDescription(parameters, -1);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) {
    CvsmSufficientStatistics cvsmStats = (CvsmSufficientStatistics) parameters;
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < cvsmStats.size(); i++) {
      sb.append(valueNames.get(i));
      sb.append("\n");
      sb.append(cvsmStats.getSufficientStatistics(i).getDescription());
      sb.append("\n");
    }
    return sb.toString();
  }
  
  
  private static class ParameterLazyLowRankTensor implements LazyLowRankTensor {
    private static final long serialVersionUID = 1L;

    private final LrtFamily family;
    private final CvsmSufficientStatistics parameters;
    private final int index;
    private LowRankTensor tensor;
    
    public ParameterLazyLowRankTensor(LrtFamily family, 
        CvsmSufficientStatistics parameters, int index) {
      this.family = Preconditions.checkNotNull(family);
      this.parameters = Preconditions.checkNotNull(parameters);
      this.index = index;
    }
    
    public LowRankTensor get() {
      if (tensor == null) {
        LowRankTensor computed = family.getModelFromParameters(parameters.getSufficientStatistics(index));
        tensor = computed;
      }
      return tensor;
    }
  }
}
