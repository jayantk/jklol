package com.jayantkrish.jklol.ccg;

import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;

public interface CcgFeatureFactory {
  
  ParametricFactor getDependencyFeatures(VariableNumMap semanticHeadVar, VariableNumMap 
      semanticArgNumVar, VariableNumMap semanticArgVar);

}
