package com.jayantkrish.jklol.ccg.lambda;

import java.util.List;

/**
 * An environment binding names to types.
 * 
 * @author jayantk
 */
public interface TypeContext {

  Type getTypeForName(String name);

  TypeContext bindNames(List<String> names, List<Type> types);

  /**
   * Returns the most general type that is a subtype of both
   * {@code type1} and {@code type2}. Returns {@code null} if
   * no such type exists.
   * 
   * @param type1
   * @param type2
   * @return
   */
  Type unify(Type type1, Type type2);
}
