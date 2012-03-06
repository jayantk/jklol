package com.jayantkrish.jklol.models;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.FactorGraphProtos.VariableProto;

/**
 * Static methods for manipulating {@code Variable}s.
 * 
 * @author jayantk
 */
public class Variables {

  public static Variable fromProto(VariableProto proto) { 
    Preconditions.checkArgument(proto.hasType());
    switch (proto.getType().getNumber()) {
    case VariableProto.VariableType.DISCRETE_VALUE:
      Preconditions.checkArgument(proto.hasDiscreteVariable());
      return DiscreteVariable.fromProto(proto.getDiscreteVariable());
    case VariableProto.VariableType.DISCRETE_OBJECT_VALUE:
      Preconditions.checkArgument(proto.hasDiscreteObjectVariable());
      return ObjectVariable.fromProto(proto.getDiscreteObjectVariable());
    default:
      throw new IllegalArgumentException("Illegal VariableProto type: " + proto.getType());  
    }
  }
}
