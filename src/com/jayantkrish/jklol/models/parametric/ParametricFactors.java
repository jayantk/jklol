package com.jayantkrish.jklol.models.parametric;

import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.loglinear.DiscreteLogLinearFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraphProtos.ParametricFactorProto;
import com.jayantkrish.jklol.util.IndexedList;

public class ParametricFactors {

  public static ParametricFactor fromProto(ParametricFactorProto proto,
      IndexedList<Variable> variableTypeIndex) {
    switch (proto.getType()) {
    case DISCRETE_LOG_LINEAR:
      DiscreteLogLinearFactor.fromProto(proto, variableTypeIndex);
    default:
      throw new IllegalArgumentException("Invalid factor type: " + proto.getType());  
    }
  }
}
