package com.jayantkrish.jklol.ccg.lambda;

import java.util.Collections;
import java.util.Map;

import com.google.common.base.Preconditions;

/**
 * Type declaration that gets type information from the
 * suffix of each constant. Constants with declared types
 * must end in ":(type)".
 * 
 * @author jayantk
 *
 */
public class ExplicitTypeDeclaration extends AbstractTypeDeclaration {
  
  private final Map<String, Type> typeReplacements;
  
  public ExplicitTypeDeclaration(Map<String, String> supertypeMap,
      Map<String, Type> typeReplacements) {
    super(supertypeMap);
    this.typeReplacements = Preconditions.checkNotNull(typeReplacements);
  }
  
  public static ExplicitTypeDeclaration getDefault() {
    return new ExplicitTypeDeclaration(Collections.emptyMap(), Collections.emptyMap());
  }

  @Override
  public Type getType(String constant) {
    String[] parts = constant.split(":");
    if (parts.length > 1) {
      // The expression has a type declaration
      String typeString = parts[parts.length - 1];
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
      return typeReplacements.get(typeString);
    } else {
      return type; 
    }
  }
}
