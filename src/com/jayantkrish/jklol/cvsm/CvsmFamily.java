package com.jayantkrish.jklol.cvsm;

import java.util.Collections;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.cvsm.Cvsm.CvsmParameters;
import com.jayantkrish.jklol.cvsm.CvsmSufficientStatistics.ParametricFamilySupplier;
import com.jayantkrish.jklol.cvsm.lrt.LowRankTensor;
import com.jayantkrish.jklol.models.parametric.ParametricFamily;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.models.parametric.TensorSufficientStatistics;
import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.IndexedList;

public class CvsmFamily implements ParametricFamily<Cvsm> {
  private static final long serialVersionUID = 2L;
  
  private final IndexedList<String> valueNames;
  private final List<LrtFamily> families;
  private final List<Supplier<SufficientStatistics>> suppliers;
  
  public CvsmFamily(IndexedList<String> valueNames, List<LrtFamily> families) {
    this.valueNames = Preconditions.checkNotNull(valueNames);
    this.families = Preconditions.checkNotNull(families);
    this.suppliers = Lists.newArrayList();
    for (LrtFamily family : families) {
      suppliers.add(new ParametricFamilySupplier(family));
    }
    Preconditions.checkArgument(valueNames.size() == families.size());
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    List<SufficientStatistics> parameters = Collections.<SufficientStatistics>nCopies(families.size(), null);
    return new CvsmSufficientStatistics(valueNames, suppliers, parameters);
  }

  /**
   * Initialize the matrices and tensors in {@code parameters} to the
   * identity. Assumes the initial values of these parameters are 0.
   *  
   * @param parameters
   */
  public void initializeParametersToIdentity(SufficientStatistics parameters) {
    CvsmSufficientStatistics cvsmStats = (CvsmSufficientStatistics) parameters;
    Preconditions.checkArgument(cvsmStats.size() == families.size());

    for (int i = 0; i < cvsmStats.size(); i++) {
      SufficientStatistics curStats = cvsmStats.getSufficientStatistics(i);
      LrtFamily lrtFamily = families.get(i);
      if (lrtFamily.getDimensionNumbers().length >= 2) {
        if (curStats instanceof TensorSufficientStatistics) {
          TensorSufficientStatistics tensorStats = (TensorSufficientStatistics) curStats;
          Tensor tensor = tensorStats.get();
          Tensor diag = SparseTensor.diagonal(tensor.getDimensionNumbers(),
              tensor.getDimensionSizes(), 1.0);
          tensorStats.increment(diag, 1.0);
        } else {
          List<SufficientStatistics> stats = curStats.coerceToList().getStatistics();
          TensorSufficientStatistics diagStats = (TensorSufficientStatistics) stats.get(stats.size() - 1);
          Tensor tensor = diagStats.get();

          DenseTensor increment = DenseTensor.constant(tensor.getDimensionNumbers(), tensor.getDimensionSizes(), 1.0);
          diagStats.increment(increment, 1.0);
        }
      }
    }
  }

  /**
   * Set the initial tensors and matrices in this family to the identity.
   * This method differs from {@link #initializeParametersToIdentity} in 
   * the way that regularization affects the parameters.
   *  
   * @param parameters
   */
  public void setInitialTensorsToIdentity() {
    for (int i = 0; i < families.size(); i++) {
      LrtFamily lrtFamily = families.get(i);
      if (lrtFamily.getDimensionNumbers().length >= 2) {
	  Tensor initialValue = SparseTensor.diagonal(lrtFamily.getDimensionNumbers(),
				lrtFamily.getDimensionSizes(), 1.0);
	  lrtFamily.setInitialTensor(initialValue);
      }
    }
  }

  public List<LrtFamily> getFamilies() {
    return families;
  }

  @Override
  public Cvsm getModelFromParameters(SufficientStatistics parameters) {
    CvsmSufficientStatistics cvsmStats = (CvsmSufficientStatistics) parameters;
    return new Cvsm(valueNames, new ParameterCvsmParameters(families, cvsmStats));
  }

  public void incrementSufficientStatistics(CvsmGradient increment, Cvsm currentValues,
      SufficientStatistics parameters) {
    List<String> tensorNames = increment.getTensorNames();
    List<LowRankTensor> tensors = increment.getTensors();
    
    for (int i = 0; i < tensorNames.size(); i++) {
      String name = tensorNames.get(i);
      LowRankTensor currentValue = currentValues.getTensor(name);
      incrementValueSufficientStatistics(name, currentValue, tensors.get(i), parameters, 1.0);
    }
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

  private static class ParameterCvsmParameters implements CvsmParameters {
    private static final long serialVersionUID = 1L;

    private final List<LrtFamily> families;
    private final CvsmSufficientStatistics parameters;
    private LowRankTensor[] tensors;
    
    public ParameterCvsmParameters(List<LrtFamily> families, CvsmSufficientStatistics parameters) {
      this.families = Preconditions.checkNotNull(families);
      this.parameters = Preconditions.checkNotNull(parameters);
      this.tensors = new LowRankTensor[parameters.size()];
    }

    public LowRankTensor get(int index) {
      if (tensors[index] == null) {
        LowRankTensor computed = families.get(index).getModelFromParameters(
            parameters.getSufficientStatistics(index));
        tensors[index] = computed;
      }
      return tensors[index];
    }
    
    public int size() {
      return parameters.size();
    }
  }
}
