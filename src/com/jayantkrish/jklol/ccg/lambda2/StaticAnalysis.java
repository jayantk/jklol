package com.jayantkrish.jklol.ccg.lambda2;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.ccg.lambda.Type;

/**
 * Static analysis of lambda calculus expressions. Computes
 * the variables that are free / bound at every program 
 * location, as well as the types of expressions. 
 * 
 * @author jayant
 *
 */
public class StaticAnalysis {
  
  public static final String LAMBDA = "lambda";

  private StaticAnalysis() {
    // Prevent instantiation
  }

  /**
   * Gets all free variables in {@code expression}, i.e.,
   * variables whose values are determined by the environment
   * in which the expression is evaluated.
   *  
   * @param expression
   * @return
   */
  public static Set<String> getFreeVariables(Expression2 expression) {
    Set<String> freeVariables = Sets.newHashSet();
    ScopeSet scopes = getScopes(expression);
    for (int i = 0; i < expression.size(); i++) {
      Scope scope = scopes.getScope(i);
      String varName = getFreeVariable(expression, i, scope);
      if (varName != null) {
        freeVariables.add(varName);
      }
    }
    return freeVariables;
  }
  
  /**
   * If {@code index} points to a free variable in {@code expression},
   * return its name. Otherwise, returns null.
   * 
   * @param expression
   * @param index
   * @param scope
   * @return
   */
  private static String getFreeVariable(Expression2 expression, int index, Scope scope) {
    Expression2 sub = expression.getSubexpression(index);
    if (sub.isConstant()) {
      String constant = sub.getConstant();
      if (!constant.equals(LAMBDA) && !scope.isBound(constant)) {
        return sub.getConstant();
      } 
    }
    return null;
  }
  
  /**
   * Gets the set of indexes where {@code variableName} appears
   * as a free variable in {@code expression}.
   * 
   * @code expression
   * @param variableName
   * @return
   */
  public static int[] getIndexesOfFreeVariable(Expression2 expression, String variableName) {
    List<Integer> indexes = Lists.newArrayList();
    ScopeSet scopes = getScopes(expression);
    for (int i = 0; i < expression.size(); i++) {
      Scope scope = scopes.getScope(i);
      String varName = getFreeVariable(expression, i, scope);
      if (varName != null && varName.equals(variableName)) {
        indexes.add(i);
      }
    }
    return Ints.toArray(indexes);
  }

  /**
   * Gets the scope of {@code expression} in which the
   * subexpression at {@code index} is evaluated. This scope
   * includes the bound variables, etc.
   * 
   * @param expression
   * @param index
   * @return
   */
  public static Scope getEnclosingScope(Expression2 expression, int index) {
    Preconditions.checkArgument(index < expression.size());
    ScopeSet scopes = getScopes(expression);
    return scopes.getScope(index);
  }

  public static ScopeSet getScopes(Expression2 expression) {
    Scope scope = new Scope(0, expression.size(), Maps.<String,Integer>newHashMap(), null);
    List<Scope> allCreatedScopes = Lists.newArrayList();
    allCreatedScopes.add(scope);
    for (int i = 0; i < expression.size(); i++) {
      while (i >= scope.end) {
        scope = scope.getParent();
      }

      Expression2 sub = expression.getSubexpression(i);
      if (sub.isConstant()) {
        String constant = sub.getConstant();
        if (constant.equals(LAMBDA)) {
          // Read off the variables and add them to a static scope.
          Expression2 lambdaExpression = expression.getSubexpression(i - 1);
          List<Expression2> subexpressions = lambdaExpression.getSubexpressions();

          // First expression is LAMBDA, last is body.
          Map<String, Integer> bindings = Maps.newHashMap();
          for (int j = 1; j < subexpressions.size() - 1; j++) {
            Preconditions.checkState(subexpressions.get(j).isConstant(),
                "Illegal lambda expression %s", lambdaExpression);
            bindings.put(subexpressions.get(j).getConstant(), i + j);
          }

          int scopeStart = i;
          int scopeEnd = i + lambdaExpression.size() - 1;

          scope = new Scope(scopeStart, scopeEnd, bindings, scope);
          allCreatedScopes.add(scope);
        }
      }
    }

    return new ScopeSet(allCreatedScopes);
  }
  
  public static boolean isLambda(Expression2 expression, int index) {
    Expression2 subexpression = expression.getSubexpression(index);
    return !subexpression.isConstant() && subexpression.getSubexpression(1).isConstant() && 
        subexpression.getSubexpression(1).getConstant().equals(LAMBDA);
  }
  
  public static List<String> getLambdaArguments(Expression2 expression, int index) {
    int[] children = expression.getChildIndexes(index);
    List<String> args = Lists.newArrayList();
    for (int i = 1; i < children.length - 1; i++){
      args.add(expression.getSubexpression(children[i]).getConstant());
    }
    return args;
  }

  public static Expression2 getLambdaBody(Expression2 expression, int index) {
    int[] children = expression.getChildIndexes(index);
    return expression.getSubexpression(children[children.length - 1]);
  }

  public static boolean isPartOfSpecialForm(Expression2 expression, int index) {
    int parentIndex = expression.getParentExpressionIndex(index);
    if (parentIndex == -1) {
      return false;
    } else {
      Expression2 parent = expression.getSubexpression(parentIndex);
      
      Expression2 sub = parent.getSubexpression(1);
      if (sub.isConstant() && sub.getConstant().equals(LAMBDA)) {
        int bodyIndex = parentIndex + parent.getSubexpressions().size();
        return index < bodyIndex;
      }
      return false;
    }
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
  public static Type inferType(Expression2 expression, Map<String, String> typeReplacements) {
    Map<Integer, Type> subexpressionTypeMap = Maps.newHashMap();
    initializeSubexpressionTypeMap(expression, subexpressionTypeMap);
    ScopeSet scopes = getScopes(expression);

    boolean updated = true;
    while (updated) {
      updated = false;
      for (int index : subexpressionTypeMap.keySet()) {
        Expression2 subexpression = expression.getSubexpression(index);
        if (subexpression.isConstant()) {
          String[] parts = subexpression.getConstant().split(":");
          if (parts.length > 1) {
            // The expression has a type declaration
            String typeString = parts[1];
            Type newType = doTypeReplacements(Type.parseFrom(typeString), typeReplacements);
            updated = updated || updateType(index, newType, subexpressionTypeMap, expression);
          }
          
          Scope scope = scopes.getScope(index);
          int bindingIndex = scope.getBindingIndex(subexpression.getConstant());
          if (bindingIndex != -1) {
            // Propagate type information between occurrences of the same variable.
            Type myType = subexpressionTypeMap.get(index);
            Type bindingType = subexpressionTypeMap.get(bindingIndex);
            
            updated = updated || updateType(index, bindingType, subexpressionTypeMap, expression);
            updated = updated || updateType(bindingIndex, myType, subexpressionTypeMap, expression);
          }

        } else if (subexpression.getSubexpressions().size() > 1 && 
            subexpression.getSubexpression(1).isConstant() && 
            subexpression.getSubexpression(1).getConstant().equals(LAMBDA)) {
          // Lambda expression.
          int[] childIndexes = expression.getChildIndexes(index);
          int bodyIndex = childIndexes[childIndexes.length - 1];
          
          Type newType = subexpressionTypeMap.get(bodyIndex);
          for (int i = childIndexes.length - 2; i >= 1; i--) {
            newType = newType.addArgument(subexpressionTypeMap.get(childIndexes[i]));
          }

          updated = updated || updateType(index, newType, subexpressionTypeMap, expression);
        } else {
          // Application
          int[] childIndexes = expression.getChildIndexes(index);
          int functionIndex = childIndexes[0];

          Type applicationType = subexpressionTypeMap.get(index);
          Type functionType = subexpressionTypeMap.get(functionIndex);

          if (!functionType.toString().equals("unknown")) {
            Type rest = functionType;
            
            for (int i = 1; i < childIndexes.length; i++) {
              Type argType = rest.getArgumentType();
              if (!rest.acceptsRepeatedArguments()) {
                rest = rest.getReturnType();
              }
              updated = updated || updateType(childIndexes[i], argType,
                  subexpressionTypeMap, expression);
            }

            if (rest.acceptsRepeatedArguments()) {
              rest = rest.getReturnType();
            }

            updated = updated || updateType(index, rest, subexpressionTypeMap,
                expression);
          }

          // Propagate type information on arguments and return value
          // back to the function itself.
          functionType = applicationType;
          for (int i = childIndexes.length - 1; i >= 1; i--) {
            Type argType = subexpressionTypeMap.get(childIndexes[i]);
            functionType = functionType.addArgument(argType);
          }
          updated = updated || updateType(functionIndex, functionType, subexpressionTypeMap,
              expression);
        }
      }
    }
    return subexpressionTypeMap.get(0);
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

  private static void initializeSubexpressionTypeMap(Expression2 expression,
      Map<Integer, Type> subexpressionTypeMap) {
    for (int i = 0; i < expression.size(); i++) {
      if (!(expression.isConstant() && expression.getConstant().equals(LAMBDA))) {
        // Don't include the lambda part of lambda expressions.
        subexpressionTypeMap.put(i, Type.createAtomic("unknown"));
      }
    }
  }

  private static boolean updateType(int index, Type type,
      Map<Integer, Type> typeMap, Expression2 expression) {
    Type oldType = typeMap.get(index);
    Type newType = unify(oldType, type);

    if (!newType.equals(oldType)) {
      // System.out.println(index + " " + expression.getSubexpression(index) + " " + oldType + " " + type + " -> " + newType);
      typeMap.put(index, newType);
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

  public static String getNewVariableName(Expression2 expression) {
    // TODO: do this in a canonical way
    int random = (int) (Math.random() * 1000000.0);
    return "var" + random;
  }
  
  public static List<String> getNewVariableNames(Expression2 expression, int num) {
    // TODO: warning, this may break if the above generates a single
    // canonical name for expression.
    List<String> names = Lists.newArrayList();
    for (int i = 0; i < num; i++) {
      names.add(getNewVariableName(expression));
    }
    return names;
  }

  public static class Scope {
    // Index in the program of the first expression within an
    // expression that defines a new scope. For example, in:
    // (foo (lambda x body))
    // a scope would start at index 3 and end at 6.
    private final int start;
    // End of the expression that defines a new scope.
    // Exclusive index.
    private final int end;
    
    private final Map<String, Integer> bindings;

    private final Scope parent;

    public Scope(int start, int end, Map<String, Integer> bindings, Scope parent) {
      this.start = start;
      this.end = end;
      this.bindings = Maps.newHashMap(bindings);
      this.parent = parent;
    }

    public boolean isBound(String variable) {
      return getBindingIndex(variable) != -1;
    }
    
    /**
     * Gets the index in the program where variable occurs
     * in the binding expression that added to to this scope.
     * Returns -1 if there is no such binding.
     *  
     * @param variable
     * @return
     */
    public int getBindingIndex(String variable) {
      if (bindings.containsKey(variable)) {
        return bindings.get(variable);
      } else if (parent != null) {
        return parent.getBindingIndex(variable);
      } else {
        return -1;
      }
    }

    public Scope getParent() {
      return parent;
    }

    public int getStart() {
      return start;
    }

    public int getEnd() {
      return end;
    }

    public int getDepth() {
      if (parent != null) {
        return 1 + parent.getDepth();
      } else {
        return 0;
      }
    }

    /**
     * Gets all variables bound in this scope, including
     * those bound in its parents.
     * 
     * @return
     */
    public Set<String> getBoundVariables() {
      Set<String> allBindings = Sets.newHashSet();
      if (parent != null) {
        allBindings.addAll(parent.getBoundVariables());
      } 
      allBindings.addAll(bindings.keySet());
      return allBindings;
    }
  }

  public static class ScopeSet {
    private final List<Scope> scopes;
    
    public ScopeSet(List<Scope> scopes) {
      this.scopes = ImmutableList.copyOf(scopes);
    }
    
    public Scope getScope(int index) {
      int minSize = Integer.MAX_VALUE;
      Scope best = null;
      for (Scope scope : scopes) {
        if (scope.getStart() <= index && index < scope.getEnd() && 
            scope.getEnd() - scope.getStart() < minSize) {
          best = scope;
          minSize = scope.getEnd() - scope.getStart();
        }
      }
      return best;
    }
  }
}
