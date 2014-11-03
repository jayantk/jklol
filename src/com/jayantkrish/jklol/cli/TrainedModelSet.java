package com.jayantkrish.jklol.cli;

import java.io.Serializable;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

/**
 * A {@code ParametricFactorGraph} with estimated parameters. Useful for 
 * passing around trained models, e.g., during serialization.
 *
 * @author jayantk
 */
public class TrainedModelSet implements Serializable {

  private static final long serialVersionUID = 1L;

  private final ParametricFactorGraph modelFamily;
  private final SufficientStatistics parameters;
  private final DynamicFactorGraph instantiatedModel;

  public TrainedModelSet(ParametricFactorGraph modelFamily,
      SufficientStatistics parameters, DynamicFactorGraph instantiatedModel) {
    this.modelFamily = Preconditions.checkNotNull(modelFamily);
    this.parameters = Preconditions.checkNotNull(parameters);
    this.instantiatedModel = Preconditions.checkNotNull(instantiatedModel);
  }

  public ParametricFactorGraph getModelFamily() {
    return modelFamily;
  }

  public SufficientStatistics getParameters() {
    return parameters;
  }

  public DynamicFactorGraph getInstantiatedModel() {
    return instantiatedModel;
  }
}