package com.jayantkrish.jklol.ccg.lambda;

import com.google.common.base.Preconditions;

public abstract class AbstractTypeDeclaration implements TypeDeclaration {

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
  
  public Type meet(Type t1, Type t2) {
    if (t1.equals(t2)) {
      return t1;
    } else if (t1.equals(TypeDeclaration.TOP)) {
      return t2;
    } else if (t2.equals(TypeDeclaration.TOP)) {
      return t1;
    } else {
      return TypeDeclaration.TOP;
    }
  }

  public Type join(Type t1, Type t2) {
    if (t1.equals(t2)) {
      return t1;
    } else if (t1.equals(TypeDeclaration.BOTTOM)) {
      return t2;
    } else if (t2.equals(TypeDeclaration.BOTTOM)) {
      return t1;
    } else {
      return TypeDeclaration.BOTTOM;
    }
  }

  public boolean isAtomicSubtype(String subtype, String supertype) {
    return subtype.equals(supertype);
  }
}
