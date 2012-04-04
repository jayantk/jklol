package com.jayantkrish.jklol.models.dynamic;

import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraphProtos.PlateFactorProto;
import com.jayantkrish.jklol.util.IndexedList;

public final class PlateFactors {

  public static PlateFactor fromProto(PlateFactorProto proto,
      IndexedList<Variable> variableTypeIndex) {
    switch (proto.getType()) {
    case REPLICATED:
      return ReplicatedFactor.fromProto(proto, variableTypeIndex);
    default:
      throw new IllegalArgumentException("Invalid type: " + proto.getType());
    }
  }
  
  private PlateFactors() {
    // Prevent instantiation.
  }
}
