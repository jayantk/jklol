package com.jayantkrish.jklol.ccg.lambda;

import java.util.List;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class TypedExpression {
  private final Expression expression;
  private final Type type;

  public TypedExpression(Expression expression, Type type) {
    this.expression = Preconditions.checkNotNull(expression);
    this.type = type;
  }

  /**
   * Fairly hacky implementation of type inference. Expects 
   * as an argument an expression where constants have the form
   * constant_name:type_spec.
   * 
   * TODO: return TypedExpression
   * TODO: implement unification lattice for atomic types. Fix
   * hardcoded constants in the process. Get rid of typeReplacements.
   * 
   * @param expression
   * @param typeReplacements
   * @return
   */
  public static Type inferType(Expression expression,
      Map<String, String> typeReplacements) {
    Map<Expression, Type> subexpressionTypeMap = Maps.newHashMap();
    initializeSubexpressionTypeMap(expression, subexpressionTypeMap);

    boolean updated = true;
    while (updated) {
      updated = false;
      for (Expression subexpression : subexpressionTypeMap.keySet()) {
        if (subexpression instanceof ConstantExpression) {

          String[] parts = subexpression.toString().split(":");
          if (parts.length > 1) {
            // The expression has a type declaration
            String typeString = parts[1];
            Type newType = doTypeReplacements(Type.parseFrom(typeString), typeReplacements);
            
            updated = updated || updateType(subexpression, newType, subexpressionTypeMap);
          }

        } else if (subexpression instanceof LambdaExpression) {
          LambdaExpression lambdaExpression = ((LambdaExpression) subexpression);
          Type newType = subexpressionTypeMap.get(lambdaExpression.getBody());

          int numArgs = lambdaExpression.getArguments().size(); 
          for (int i = numArgs - 1; i >= 0; i--) {
            newType = newType.addArgument(subexpressionTypeMap.get(
                lambdaExpression.getArguments().get(i)));
          }

          updated = updated || updateType(subexpression, newType, subexpressionTypeMap);
        } else if (subexpression instanceof ApplicationExpression) {
          ApplicationExpression applicationExpression = (ApplicationExpression) subexpression;
          Expression functionExpression = applicationExpression.getFunction();
          Type applicationType = subexpressionTypeMap.get(applicationExpression);
          Type functionType = subexpressionTypeMap.get(functionExpression);
          
          if (!functionType.toString().equals("unknown")) {
            Type rest = functionType;
            List<Expression> args = applicationExpression.getArguments();
            for (int i = 0; i < args.size(); i++) {
              Type argType = rest.getArgumentType();
              if (!rest.acceptsRepeatedArguments()) {
                rest = rest.getReturnType();
              }
              updated = updated || updateType(args.get(i), argType, subexpressionTypeMap);
            }

            if (rest.acceptsRepeatedArguments()) {
              rest = rest.getReturnType();
            }

            updated = updated || updateType(applicationExpression, rest, subexpressionTypeMap);
          }

          // Propagate type information on arguments and return value
          // back to the function itself.
          List<Expression> args = applicationExpression.getArguments();
          functionType = applicationType;
          for (int i = args.size() - 1; i >= 0; i--) {
            Type argType = subexpressionTypeMap.get(args.get(i));
            functionType = functionType.addArgument(argType);
          }
          updated = updated || updateType(functionExpression, functionType, subexpressionTypeMap);
        }
      }
    }
    return subexpressionTypeMap.get(expression);
  }
  
  private static Type doTypeReplacements(Type type, Map<String, String> typeReplacements) {
    if (type.isFunctional()) {
      Type newArg = doTypeReplacements(type.getArgumentType(), typeReplacements);
      Type newReturn = doTypeReplacements(type.getReturnType(), typeReplacements);
      type = Type.createFunctional(newArg, newReturn, type.acceptsRepeatedArguments());
    }
    
    String typeString = type.toString();
    
    if (typeReplacements.containsKey(typeString)) {
      return Type.parseFrom(typeReplacements.get(typeString));
    } else {
      return type; 
    }
  }
  
  private static void initializeSubexpressionTypeMap(Expression expression,
      Map<Expression, Type> subexpressionTypeMap) {
    subexpressionTypeMap.put(expression, Type.createAtomic("unknown"));
    if (expression instanceof ApplicationExpression) {
      for (Expression subexpression : ((ApplicationExpression) expression).getSubexpressions()) {
        initializeSubexpressionTypeMap(subexpression, subexpressionTypeMap);
      }
    } else if (expression instanceof LambdaExpression) {
      for (Expression subexpression : ((LambdaExpression) expression).getArguments()) {
        initializeSubexpressionTypeMap(subexpression, subexpressionTypeMap);
      }

      initializeSubexpressionTypeMap(((LambdaExpression) expression).getBody(),
          subexpressionTypeMap);
    }
  }

  private static boolean updateType(Expression expression, Type type, Map<Expression, Type> typeMap) {
    Type oldType = typeMap.get(expression);
    Type newType = unify(oldType, type);

    if (!newType.equals(oldType)) {
      System.out.println(expression + " " + oldType + " " + type + " -> " + newType);

      typeMap.put(expression, newType);
      return true;
    } else {
      return false;
    }
  }

  private static Type unify(Type t1, Type t2) {
    if (t1.toString().equals("unknown")) {
      return t2;
    } else if (t2.toString().equals("unknown")) {
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
      return Type.createAtomic("bottom");
    }
  }

  public Expression getExpression() {
    return expression;
  }

  public Type getType() {
    return type;
  }

  public static List<Expression> getExpressions(List<TypedExpression> typedExpressions) {
    List<Expression> expressions = Lists.newArrayList();
    for (TypedExpression typedExpression : typedExpressions) {
      expressions.add(typedExpression.getExpression());
    }
    return expressions;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((expression == null) ? 0 : expression.hashCode());
    result = prime * result + ((type == null) ? 0 : type.hashCode());
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
    TypedExpression other = (TypedExpression) obj;
    if (expression == null) {
      if (other.expression != null)
        return false;
    } else if (!expression.equals(other.expression))
      return false;
    if (type == null) {
      if (other.type != null)
        return false;
    } else if (!type.equals(other.type))
      return false;
    return true;
  }
}
