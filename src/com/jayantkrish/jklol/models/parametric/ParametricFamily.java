package com.jayantkrish.jklol.models.parametric;

import java.io.Serializable;

/**
 * A parametric family of models, such as distributions.
 * 
 * @author jayantk
 */
public interface ParametricFamily<T> extends Serializable {

  /**
   * Gets a new all-zero vector of parameters for {@code this}. The
   * returned vector can be an argument to methods of this instance
   * which take parameters as an argument, e.g.,
   * {@link #getFactorFromParameters()}.
   * 
   * @return
   */
  public SufficientStatistics getNewSufficientStatistics();

  /**
   * Instantiates this model from the parameter vector
   * {@code parameters}. {@code parameters} must be of appropriate
   * dimensionality, etc. in order for this method to be applicable --
   * typically, the passed-in parameters should be initialized using
   * {@link #getNewSufficientStatistics()}, then updated by addition,
   * etc.
   * 
   * @param parameters
   * @return
   */
  public T getModelFromParameters(SufficientStatistics parameters);
  
  public ParametricFamily<T> rescaleFeatures(SufficientStatistics rescaling);

  /**
   * Gets a human-interpretable string describing {@code parameters}.
   * This method returns one line per parameter, containing a
   * description of the parameter and its value. Equivalent to
   * {@link #getParameterDescription(SufficientStatistics, int)} with
   * a negative {@code numFeatures} value.
   * 
   * @param parameters
   * @return
   */
  public String getParameterDescription(SufficientStatistics parameters);

  /**
   * Gets a human-interpretable string describing {@code parameters}.
   * This method returns one line per parameter, containing a
   * description of the parameter and its value.
   * <p>
   * If {@code numFeatures >= 0}, this method returns a string
   * describing the {@code numFeatures} features with the largest
   * weights. If {@code numFeatures} is negative, all features are
   * included.
   * 
   * @param parameters
   * @param numFeatures
   * @return
   */
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures);
}
