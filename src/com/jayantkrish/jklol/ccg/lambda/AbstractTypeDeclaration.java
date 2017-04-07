package com.jayantkrish.jklol.ccg.lambda;

import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public abstract class AbstractTypeDeclaration implements TypeDeclaration {
  private static final long serialVersionUID = 1L;

  private final Map<String, String> supertypeMap;
  
  public AbstractTypeDeclaration(Map<String, String> supertypeMap) {
    this.supertypeMap = Maps.newHashMap(supertypeMap);
  }

  @Override
  public Type unify(Type t1, Type t2) {
    Preconditions.checkNotNull(t1, "Type t1 is null. t1: %s t2: %s", t1, t2);
    Preconditions.checkNotNull(t2, "Type t2 is null. t1: %s t2: %s", t1, t2);
    
    if (t1.equals(TypeDeclaration.TOP)) {
      return t2;
    } else if (t2.equals(TypeDeclaration.TOP)) {
      return t1;
    } else if (t1.equals(t2)) {
      return t1;
    } if (t1.isFunctional() && t2.isFunctional()) {
      if (t1.acceptsRepeatedArguments() == t2.acceptsRepeatedArguments()) {
        // If the argument repeats, its repeated for both, so unify that type.
        // If it doesn't repeat, then 
        Type argumentType = unify(t1.getArgumentType(), t2.getArgumentType()); 
        Type returnType = unify(t1.getReturnType(), t2.getReturnType());
        return Type.createFunctional(argumentType, returnType, t1.acceptsRepeatedArguments());
      } else {
        // Repeats for one and not the other.
        Type repeated = t1.acceptsRepeatedArguments() ? t1 : t2;
        Type unrepeated = t1.acceptsRepeatedArguments() ? t2 : t1;

        // TODO: this doesn't work if the return type of the type with
        // the repeated arguments is non-atomic.
        if (!unrepeated.getReturnType().isAtomic()) {
          Type argumentType = unify(repeated.getArgumentType(), unrepeated.getArgumentType()); 
          Type returnType = unify(repeated, unrepeated.getReturnType());
          return Type.createFunctional(argumentType, returnType, false);
        } else {
          Type argumentType = unify(repeated.getArgumentType(), unrepeated.getArgumentType()); 
          Type returnType = unify(repeated.getReturnType(), unrepeated.getReturnType());
          return Type.createFunctional(argumentType, returnType, false);
        }
      }
    } else {
      return TypeDeclaration.BOTTOM;
    }
  }
  
  @Override
  public Type meet(Type t1, Type t2) {
    Preconditions.checkArgument(t1.isAtomic() && t2.isAtomic() &&
        !t1.hasTypeVariables() && !t2.hasTypeVariables());
    String t1Atomic = t1.getAtomicTypeName();
    String t2Atomic = t2.getAtomicTypeName();
    
    if (isAtomicSubtype(t1Atomic, t2Atomic)) {
      return t1;
    } else if (isAtomicSubtype(t2Atomic, t1Atomic)) {
      return t2;
    } else {
      return TypeDeclaration.BOTTOM;
    }
  }

  @Override
  public Type join(Type t1, Type t2) {
    Preconditions.checkArgument(t1.isAtomic() && t2.isAtomic() &&
        !t1.hasTypeVariables() && !t2.hasTypeVariables());
    String t1Atomic = t1.getAtomicTypeName();
    String t2Atomic = t2.getAtomicTypeName();

    Set<String> t1Supertypes = Sets.newHashSet();
    String curType = t1Atomic;
    while (curType != null) {
      t1Supertypes.add(curType);
      curType = supertypeMap.get(curType);
    }
    
    curType = t2Atomic;
    while (curType != null) {
      if (t1Supertypes.contains(curType)) {
        return Type.createAtomic(curType);
      }
      curType = supertypeMap.get(curType);
    }

    return TypeDeclaration.TOP;
  }

  @Override
  public boolean isAtomicSubtype(String subtype, String supertype) {
    if (supertype.equals(TypeDeclaration.TOP.getAtomicTypeName())) {
      return true;
    } else if (subtype.equals(TypeDeclaration.BOTTOM.getAtomicTypeName())) {
      return true;
    } else {
      String curType = subtype;
      while (curType != null) {
        if (curType.equals(supertype)) {
          return true;
        }
        curType = supertypeMap.get(curType);
      }
      return false;
    }
  }
}
