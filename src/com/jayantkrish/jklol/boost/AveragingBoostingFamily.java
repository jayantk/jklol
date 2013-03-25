package com.jayantkrish.jklol.boost;

import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.Factors;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.models.parametric.TensorSufficientStatistics;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.util.Assignment;

/**
 * 
 * @author jayantk
 */
public class AveragingBoostingFamily extends AbstractBoostingFactorFamily {
  
  public AveragingBoostingFamily(VariableNumMap unconditionalVars) {
    super(VariableNumMap.emptyMap(), unconditionalVars);
    Preconditions.checkArgument(unconditionalVars.getDiscreteVariables().size() 
        == unconditionalVars.getVariables().size());
  }
  
  @Override
  public FunctionalGradient getNewFunctionalGradient() {
    return FactorFunctionalGradient.empty();
  }
  
  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    VariableNumMap vars = getVariables();
    return TensorSufficientStatistics.createSparse(vars, 
        SparseTensor.empty(vars.getVariableNumsArray(), vars.getVariableSizes()));
  }

  @Override
  public Factor getModelFromParameters(SufficientStatistics statistics) {
    return new TableFactor(getVariables(), ((TensorSufficientStatistics) statistics).get().elementwiseExp()); 
  }

  @Override
  public void incrementGradient(FunctionalGradient gradient, Factor regressionTarget,
      Assignment regressionAssignment) {
    ((FactorFunctionalGradient) gradient).addExample(regressionTarget, regressionAssignment);
  }

  @Override
  public SufficientStatistics projectGradient(FunctionalGradient gradient) {
    FactorFunctionalGradient factorGradient = (FactorFunctionalGradient) gradient;
    
    List<Factor> regressionTargets = factorGradient.getRegressionTargets();
    Factor mean = Factors.add(regressionTargets).product(1.0 / regressionTargets.size());

    Preconditions.checkState(mean.getVars().equals(getVariables()));
    return TensorSufficientStatistics.createSparse(mean.getVars(), mean.coerceToDiscrete().getWeights());
  }
  
  @Override
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) {
    TableFactor weightFactor = new TableFactor(getVariables(), ((TensorSufficientStatistics) parameters).get());
    List<Assignment> assignments = weightFactor.product(weightFactor).getMostLikelyAssignments(numFeatures);
    return weightFactor.describeAssignments(assignments);
  }
}
