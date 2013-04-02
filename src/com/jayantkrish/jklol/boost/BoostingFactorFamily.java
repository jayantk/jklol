package com.jayantkrish.jklol.boost;

import java.io.Serializable;

import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.util.Assignment;

public interface BoostingFactorFamily extends Serializable {

  /**
   * Gets all of the variables this family is defined over.
   * 
   * @return
   */
  VariableNumMap getVariables();

  /**
   * Gets the variables which are conditioned on. During functional
   * gradient regression, each training example consists of an
   * assignment to these variables, and a set of weights for all
   * assignments to all other variables.
   * 
   * @return
   */
  VariableNumMap getConditionalVariables();
  
  VariableNumMap getUnconditionalVariables();
  
  FunctionalGradient getNewFunctionalGradient();
  
  SufficientStatistics getNewSufficientStatistics();
  
  Factor getModelFromParameters(SufficientStatistics statistics);
  
  void incrementGradient(FunctionalGradient gradient, Factor marginal, Assignment assignment);
  
  SufficientStatistics projectGradient(FunctionalGradient gradient);
  
  String getParameterDescription(SufficientStatistics parameters);
  
  String getParameterDescription(SufficientStatistics parameters, int numFeatures);
}
