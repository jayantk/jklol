package com.jayantkrish.jklol.models.dynamic;

import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableProtos.VariablePatternProto;
import com.jayantkrish.jklol.util.IndexedList;

public final class VariablePatterns {
  
  public static VariablePattern fromProto(VariablePatternProto proto,
      IndexedList<Variable> variableTypeIndex) {
    switch (proto.getType()) {
    case NAME:
      return VariableNamePattern.fromProto(proto, variableTypeIndex);
    case WRAPPER:
      return WrapperVariablePattern.fromProto(proto, variableTypeIndex);
    default:
      throw new IllegalArgumentException("Invalid type: " + proto.getType());
    }
  }
}
