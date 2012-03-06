package com.jayantkrish.jklol.models;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.FactorGraphProtos.DiscreteObjectVariableProto;
import com.jayantkrish.jklol.models.FactorGraphProtos.VariableProto;

/**
 * A {@link Variable} which can take any value of a given type.
 *  
 * @author jayantk
 */
public class ObjectVariable implements Variable {
  
  private final Class<?> type;
  
  public ObjectVariable(Class<?> type) {
    this.type = type;
  }
  
  public static ObjectVariable fromProto(DiscreteObjectVariableProto proto) {
    Preconditions.checkArgument(proto.hasJavaClassName());
    try {
      return new ObjectVariable(Class.forName(proto.getJavaClassName()));
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(
          "Could not deserialized DiscreteObjectVariableProto. Invalid java class name.", e);
    }
  }

  @Override
  public Object getArbitraryValue() {
    return null;
  }

  @Override
  public boolean canTakeValue(Object value) {
    return type.isInstance(value);
  }
  
  public Class<?> getObjectType() {
    return type;
  }
  
  @Override
  public VariableProto toProto() {
    VariableProto.Builder builder = VariableProto.newBuilder();
    builder.setType(VariableProto.VariableType.DISCRETE_OBJECT);
    builder.getDiscreteObjectVariableBuilder().setJavaClassName(type.getName());
    return builder.build(); 
  }
  
  @Override
  public String toString() {
    return "ObjectVariable(" + type.toString() + ")";
  }
}
