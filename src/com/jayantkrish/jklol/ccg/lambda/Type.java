package com.jayantkrish.jklol.ccg.lambda;

import java.io.Serializable;

public class Type implements Serializable {
  private static final long serialVersionUID = 1L;

  // Non-null if this is an atomic type
  private final String atomicType;
  
  // Non-null if this is a function type.
  private final Type argType;
  private final Type returnType;

  private Type(String atomicType, Type argType, Type returnType) {
    this.atomicType = atomicType;
    this.argType = argType;
    this.returnType = returnType;
  }
  
  public static Type createAtomic(String type) {
    return new Type(type, null, null);
  }

  public static Type createFunctional(Type argType, Type returnType) {
    return new Type(null, argType, returnType);
  }

  public boolean isAtomic() {
    return atomicType != null;
  }

  public String getAtomicTypeName() {
    return atomicType;
  }

  public boolean isFunctional() {
    return atomicType == null;
  }

  public Type getArgumentType() {
    return argType;
  }

  public Type getReturnType() {
    return returnType;
  }
  
  @Override
  public String toString() {
    if (isAtomic()) {
      return atomicType;
    } else {
      return "<" + argType + "," + returnType + ">";
    }
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((argType == null) ? 0 : argType.hashCode());
    result = prime * result + ((atomicType == null) ? 0 : atomicType.hashCode());
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
    if (returnType == null) {
      if (other.returnType != null)
        return false;
    } else if (!returnType.equals(other.returnType))
      return false;
    return true;
  }
}
