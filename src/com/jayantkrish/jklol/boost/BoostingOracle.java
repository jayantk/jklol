package com.jayantkrish.jklol.boost;

import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.LogFunction;

public interface BoostingOracle<M, F, E> {

  F initializeFunctionalGradient();
  
  M instantiateModel(SufficientStatisticsEnsemble parameters);
  
  void accumulateGradient(F functionalGradient, M model, E example, LogFunction log);
  
  SufficientStatistics projectGradient(F functionalGradient);
}
