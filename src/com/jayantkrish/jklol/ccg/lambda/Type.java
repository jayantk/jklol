package com.jayantkrish.jklol.ccg.lambda;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;

public class Type implements Serializable {
  private static final long serialVersionUID = 1L;

  // Either atomicType is non-null or atomicTypeVar >= 0
  // if this is an atomic type. 
  private final String atomicType;
  private final int atomicTypeVar;

  // Non-null if this is a function type.
  private final Type argType;
  private final Type returnType;
  // If true, this is a function that accepts any number of
  // argType arguments.
  private final boolean acceptRepeatedArguments;

  private Type(String atomicType, int atomicTypeVar,
      Type argType, Type returnType, boolean acceptRepeatedArguments) {
    this.atomicType = atomicType;
    this.atomicTypeVar = atomicTypeVar;
    this.argType = argType;
    this.returnType = returnType;
    this.acceptRepeatedArguments = acceptRepeatedArguments;
  }

  public static Type createAtomic(String type) {
    return new Type(type, -1, null, null, false);
  }

  public static Type createTypeVariable(int id) {
    Preconditions.checkArgument(id >= 0, "Type variables must have positive ids (got %s)", id);
    return new Type(null, id, null, null, false);
  }

  public static Type createFunctional(Type argType, Type returnType,
      boolean acceptRepeatedArguments) {
    return new Type(null, -1, argType, returnType, acceptRepeatedArguments);
  }
  
  public static Type parseFrom(String spec) {
    return ExpressionParser.typeParser().parse(spec);
  }

  public boolean isAtomic() {
    return atomicType != null || atomicTypeVar >= 0;
  }

  public String getAtomicTypeName() {
    return atomicType;
  }

  public int getAtomicTypeVar() {
    return atomicTypeVar;
  }

  public boolean isFunctional() {
    return !isAtomic();
  }

  public Type getArgumentType() {
    return argType;
  }

  public Type getReturnType() {
    return returnType;
  }
  
  public boolean hasTypeVariables() {
    if (isAtomic()) {
      return atomicTypeVar >= 0;
    } else {
      return argType.hasTypeVariables() || returnType.hasTypeVariables();
    }
  }
  
  public Set<Integer> getTypeVariables() {
    Set<Integer> typeVars = Sets.newHashSet();
    getTypeVariablesHelper(typeVars);
    return typeVars;
  }
  
  private void getTypeVariablesHelper(Set<Integer> vars) {
    if (isAtomic()) {
      if (atomicTypeVar > 0) {
        vars.add(atomicTypeVar);
      }
    } else {
      argType.getTypeVariablesHelper(vars);
      returnType.getTypeVariablesHelper(vars);
    }
  }
  
  public Type addArgument(Type arg) {
    return Type.createFunctional(arg, this, false);
  }
  
  /**
   * Adds a list of argument types to this type. The
   * last argument in the list is the first argument
   * accepted by the returned type. For example, if
   * the list is [a, b, c], the resulting type is
   * (c, (b, (a, this)))
   * 
   * @param argTypes
   * @return
   */
  public Type addArguments(List<Type> argTypes) {
    Type t = this;
    for (Type a : argTypes) {
      t = t.addArgument(a);
    }
    return t;
  }

  public boolean acceptsRepeatedArguments() {
    return acceptRepeatedArguments;
  }
  
  public Type substitute(Map<Integer, Type> typeVarBindings) {
    if (isAtomic()) {
      if (typeVarBindings.containsKey(atomicTypeVar)) {
        return typeVarBindings.get(atomicTypeVar);
      } else {
        return this;
      }
    } else {
      Type newArg = argType.substitute(typeVarBindings);
      Type newReturn = returnType.substitute(typeVarBindings);
      
      if (newArg != argType || newReturn != returnType) {
        return Type.createFunctional(newArg, newReturn, acceptRepeatedArguments);
      } else {
        return this;
      }
    }
  }

  @Override
  public String toString() {
    if (isAtomic()) {
      if (hasTypeVariables()) {
        return "$" + atomicTypeVar;
      } else {
        return atomicType;
      }
    } else {
      return "<" + argType + (acceptRepeatedArguments ? "*" : "") + "," + returnType + ">";
    }
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + (acceptRepeatedArguments ? 1231 : 1237);
    result = prime * result + ((argType == null) ? 0 : argType.hashCode());
    result = prime * result + ((atomicType == null) ? 0 : atomicType.hashCode());
    result = prime * result + atomicTypeVar;
    result = prime * result + ((returnType == null) ? 0 : returnType.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    Type other = (Type) obj;
    if (acceptRepeatedArguments != other.acceptRepeatedArguments)
      return false;
    if (argType == null) {
      if (other.argType != null)
        return false;
    } else if (!argType.equals(other.argType))
      return false;
    if (atomicType == null) {
      if (other.atomicType != null)
        return false;
    } else if (!atomicType.equals(other.atomicType))
      return false;
    if (atomicTypeVar != other.atomicTypeVar)
      return false;
    if (returnType == null) {
      if (other.returnType != null)
        return false;
    } else if (!returnType.equals(other.returnType))
      return false;
    return true;
  }
}
