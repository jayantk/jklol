package com.jayantkrish.jklol.ccg.lambda;

import java.util.List;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

public class TypeContext {
  
  private Map<String, Type> bindings;
  
  public TypeContext(Map<String, Type> bindings) {
    this.bindings = Preconditions.checkNotNull(bindings);
  }

  public static TypeContext empty() {
    return new TypeContext(Maps.<String, Type>newHashMap());
  }

  public Type getTypeForName(String name) {
    return bindings.get(name);
  }

  public TypeContext bindNames(List<String> names, List<Type> types) {
    Preconditions.checkArgument(names.size() == types.size());
    Map<String, Type> newBindings = Maps.newHashMap(bindings);

    for (int i = 0; i < names.size(); i++) {
      newBindings.put(names.get(i), types.get(i));
    }

    return new TypeContext(newBindings);
  }

  public Type unify(Type type1, Type type2) {
    if (type1 == null || type2 == null) {
      return null;
    }

    if (type1.isAtomic() && type2.isAtomic()) {
      if (type1.getAtomicTypeName().equals(type2.getAtomicTypeName())) {
        return type1;
      }
    } else if (type1.isFunctional() && type2.isFunctional()){
      Type unifiedArg = unify(type1.getArgumentType(), (type2.getArgumentType()));
      Type unifiedReturn = unify(type1.getReturnType(), (type2.getReturnType()));
      
      if (unifiedArg != null && unifiedReturn != null) {
        return Type.createFunctional(unifiedArg, unifiedReturn);
      }
    }
    return null;
  }
}
