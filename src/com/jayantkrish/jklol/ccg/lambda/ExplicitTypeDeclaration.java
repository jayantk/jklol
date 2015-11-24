package com.jayantkrish.jklol.ccg.lambda;

import java.util.Collections;
import java.util.Map;

import com.google.common.base.Preconditions;

public class ExplicitTypeDeclaration implements TypeDeclaration {
  
  private final Map<String, String> typeReplacements;
  
  public ExplicitTypeDeclaration(Map<String, String> typeReplacements) {
    this.typeReplacements = Preconditions.checkNotNull(typeReplacements);
  }
  
  public static ExplicitTypeDeclaration getDefault() {
    return new ExplicitTypeDeclaration(Collections.emptyMap());
  }

  @Override
  public Type getType(String constant) {
    String[] parts = constant.split(":");
    if (parts.length > 1) {
      // The expression has a type declaration
      String typeString = parts[1];
      return doTypeReplacements(Type.parseFrom(typeString));
    }
    return TypeDeclaration.TOP;
  }
  
  private Type doTypeReplacements(Type type) {
    if (type.isFunctional()) {
      Type newArg = doTypeReplacements(type.getArgumentType());
      Type newReturn = doTypeReplacements(type.getReturnType());
      type = Type.createFunctional(newArg, newReturn, type.acceptsRepeatedArguments());
    }

    String typeString = type.toString();

    if (typeReplacements.containsKey(typeString)) {
      return Type.parseFrom(typeReplacements.get(typeString));
    } else {
      return type; 
    }
  }
  
  @Override
  public Type unify(Type t1, Type t2) {
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
}
