package com.jayantkrish.jklol.ccg.lambda;

import java.util.List;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

/**
 * Type context which stores (name, type) bindings in a map.
 *
 * @author jayantk
 */
public class MapTypeContext implements TypeContext {

  private Map<String, Type> bindings;

  public MapTypeContext(Map<String, Type> bindings) {
    this.bindings = Preconditions.checkNotNull(bindings);
  }

  /**
   * Gets an empty context (containing no name bindings).
   * @return
   */
  public static MapTypeContext empty() {
    return new MapTypeContext(Maps.<String, Type>newHashMap());
  }

  /**
   * Reads in a collection of names with type declarations.
   * Each line is a colon-separated name:type pair, e.g.,
   * <code>
   * exists:<<e,t>,t>
   * </code>
   * 
   * @param lines
   * @return
   */
  public static MapTypeContext readTypeDeclarations(List<String> lines) {
    List<TypedExpression> typedExpressions = ExpressionParser.typedLambdaCalculus().parse(lines);
    Map<String, Type> typeBindings = Maps.newHashMap();
    for (TypedExpression typedExpression : typedExpressions) {
      typeBindings.put(((ConstantExpression)typedExpression.getExpression()).getName(),
          typedExpression.getType());
    }
    return new MapTypeContext(typeBindings);
  }

  @Override
  public Type getTypeForName(String name) {
    return bindings.get(name);
  }

  @Override
  public TypeContext bindNames(List<String> names, List<Type> types) {
    Preconditions.checkArgument(names.size() == types.size());
    Map<String, Type> newBindings = Maps.newHashMap(bindings);

    for (int i = 0; i < names.size(); i++) {
      newBindings.put(names.get(i), types.get(i));
    }

    return new MapTypeContext(newBindings);
  }

  @Override
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
        return Type.createFunctional(unifiedArg, unifiedReturn, false);
      }
    }
    return null;
  }
}
