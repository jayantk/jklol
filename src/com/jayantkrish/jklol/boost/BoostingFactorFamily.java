package com.jayantkrish.jklol.boost;

import java.io.Serializable;

import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A factor in a boosted graphical model. This interface represents 
 * a particular family of regression functions which can be used to
 * approximate functional gradients. The methods of this class enable
 * regressors to be trained from functional gradients, resulting in
 * regressor-specific parameters. This class also maps from regressor
 * parameters to {@code Factor}s that use a regressor to produce a
 * probability distribution.  
 *    
 * @author jayant
 */
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
  
  /**
   * Gets the variables which are not conditioned on. Functional gradient
   * regression provides a probability distribution over these variables.
   *  
   * @return
   */
  VariableNumMap getUnconditionalVariables();
  
  /**
   * Gets a functional gradient which accumulates the sufficient
   * statistics necessary for training a regressor of this family.
   *  
   * @return
   */
  FunctionalGradient getNewFunctionalGradient();
  
  void incrementGradient(FunctionalGradient gradient, Factor marginal, Assignment assignment);
  
  SufficientStatistics projectGradient(FunctionalGradient gradient);

  /**
   * Gets a parameter vector for a regressor in this family. 
   * Instantiating a factor using the returned parameters (without 
   * mutation) should produce the uniform distribution. 
   *  
   * @return
   */
  SufficientStatistics getNewSufficientStatistics();
  
  Factor getModelFromParameters(SufficientStatistics statistics);
  
  String getParameterDescription(SufficientStatistics parameters);
  
  String getParameterDescription(SufficientStatistics parameters, int numFeatures);
}
