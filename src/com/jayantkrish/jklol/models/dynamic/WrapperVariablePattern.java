package com.jayantkrish.jklol.models.dynamic;

import java.util.Collections;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.VariableProtos.VariablePatternProto;
import com.jayantkrish.jklol.models.VariableProtos.VariablePatternProto.Type;
import com.jayantkrish.jklol.util.IndexedList;

/**
 * Pattern that identifies a fixed set of variables, represented by a {@code VariableNumMap}.
 * 
 * @author jayant
 */
public class WrapperVariablePattern extends AbstractVariablePattern {

  private final VariableNumMap variables;
  
  public WrapperVariablePattern(VariableNumMap variables) {
    this.variables = variables;
  }
  
  @Override
  public List<VariableMatch> matchVariables(VariableNumMap inputVariables) {
    if (inputVariables.containsAll(variables)) {
      return Lists.newArrayList(new VariableMatch(variables));
    } else {
      return Collections.emptyList();
    }
  }
  
  @Override
  public VariablePatternProto toProto(IndexedList<Variable> variableTypeIndex) {
    VariablePatternProto.Builder builder = VariablePatternProto.newBuilder();
    builder.setType(Type.WRAPPER);
    builder.getWrapperProtoBuilder().setVariables(variables.toProto(variableTypeIndex));
    return builder.build();
  }
  
  public static WrapperVariablePattern fromProto(VariablePatternProto proto,
      IndexedList<Variable> variableTypeIndex) {
    Preconditions.checkArgument(proto.getType().equals(Type.WRAPPER));
    Preconditions.checkArgument(proto.hasWrapperProto());
    Preconditions.checkArgument(proto.getWrapperProto().hasVariables());
    
    return new WrapperVariablePattern(VariableNumMap.fromProto(
        proto.getWrapperProto().getVariables(), variableTypeIndex));
  }
}
