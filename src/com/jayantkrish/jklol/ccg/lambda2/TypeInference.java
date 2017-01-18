package com.jayantkrish.jklol.ccg.lambda2;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.ccg.lambda.Type;
import com.jayantkrish.jklol.ccg.lambda.TypeDeclaration;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis.Scope;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis.ScopeSet;

/**
 * Type inference algorithm for simply-typed lambda calculus 
 * with subtypes and polymorphism. The algorithm can infer
 * the types of lambda variables given a type declaration 
 * for the types for constants. Correct usage is:
 * 
 * <code>
 * TypeInference t = new TypeInference(expression, typeDeclaration);
 * t.infer();
 * t.getExpressionTypes();
 * </code>
 * 
 * @author jayantk
 *
 */
public class TypeInference {

  private final Expression2 expression;
  private final TypeDeclaration typeDeclaration;
  
  private Map<Integer, Type> expressionTypes;
  private ScopeSet scopes;
  private ConstraintSet constraints = null;
  private ConstraintSet solved = null;
  
  public TypeInference(Expression2 expression, TypeDeclaration typeDeclaration) {
    this.expression = Preconditions.checkNotNull(expression);
    this.typeDeclaration = Preconditions.checkNotNull(typeDeclaration);
  }
  
  /**
   * Gets a mapping from indexes into {@code expression} to
   * their corresponding types. {@code infer()} must be invoked
   * before this method.
   * 
   * @return
   */
  public Map<Integer, Type> getExpressionTypes() {
    return expressionTypes;
  }

  /**
   * Gets the collection of typing constraints generated
   * during type inference, before running the constraint
   * solver. {@code infer()} must be invoked
   * before this method.
   *  
   * @return
   */
  public ConstraintSet getConstraints() {
    return constraints;
  }

  /**
   * Gets the solved collection of type constraints. {@code infer()}
   * must be invoked before this method.
   *  
   * @return
   */
  public ConstraintSet getSolvedConstraints() {
    return solved;
  }
  
  /**
   * Run type inference.
   */
  public void infer() {
    scopes = StaticAnalysis.getScopes(expression);
    expressionTypes = Maps.newHashMap();
    constraints = ConstraintSet.empty();
    for (Scope scope : scopes.getScopes()) {
      for (String variable : scope.getBoundVariables()) {
        int location = scope.getBindingIndex(variable);
        if (!expressionTypes.containsKey(location)) {
          expressionTypes.put(location, constraints.getFreshTypeVar());
        }
      }
    }

    populateExpressionTypes(0);
    // TODO: root type.
    solved = constraints.solve(typeDeclaration);

    for (int k : expressionTypes.keySet()) {
      expressionTypes.put(k, expressionTypes.get(k).substitute(solved.getBindings()));
    }
  }

  private void populateExpressionTypes(int index) {
    Expression2 subexpression = expression.getSubexpression(index);
    if (subexpression.isConstant()) {
      Scope scope = scopes.getScope(index);
      int bindingIndex = scope.getBindingIndex(subexpression.getConstant());
      if (bindingIndex == -1) {
        // Get the type of this constant if it is declared
        // and it's not a lambda variable.
        Type type = typeDeclaration.getType(subexpression.getConstant());
        if (type.hasTypeVariables()) {
          Map<Integer, Type> substitutions = Maps.newHashMap();
          Set<Integer> usedTypeVars = type.getTypeVariables();
          for (int var : usedTypeVars) {
            Type fresh = constraints.getFreshTypeVar();
            while (usedTypeVars.contains(fresh.getAtomicTypeVar())) {
              fresh = constraints.getFreshTypeVar();
            }
            substitutions.put(var, fresh);
          }
          type = type.substitute(substitutions);
        }
        
        expressionTypes.put(index, type);
      } else {
        // Get the type variable for this binding and add it here.
        Type boundType = expressionTypes.get(bindingIndex);
        expressionTypes.put(index, boundType);
      }
    } else if (StaticAnalysis.isLambda(subexpression)) {
      int[] childIndexes = expression.getChildIndexes(index);
      int bodyIndex = childIndexes[childIndexes.length - 1];
      int[] argIndexes = expression.getChildIndexes(childIndexes[1]);

      populateExpressionTypes(bodyIndex);
      Type lambdaType = expressionTypes.get(bodyIndex);
      for (int i = argIndexes.length - 1; i >= 0; i--) {
        // Lambda arguments have prepopulated expression types
        // from the initialization.
        lambdaType = lambdaType.addArgument(expressionTypes.get(argIndexes[i]));
      }

      expressionTypes.put(index, lambdaType);
    } else {
      // Function application
      int[] childIndexes = expression.getChildIndexes(index);
      int functionIndex = childIndexes[0];

      populateExpressionTypes(functionIndex);
      Type functionType = expressionTypes.get(functionIndex);

      Type returnType = constraints.getFreshTypeVar();
      Type supertype = returnType;
      for (int i = childIndexes.length - 1; i >= 1; i--) {
        populateExpressionTypes(childIndexes[i]);
        
        Type argType = expressionTypes.get(childIndexes[i]);
        Type argSupertype = constraints.upcast(argType, true);

        supertype = supertype.addArgument(argSupertype);
      }

      // Hack to handle types with vararg parameters.
      Type functionVarargType = functionType;
      List<Type> varargs = Lists.newArrayList();
      boolean anyVararg = false;
      for (int i = childIndexes.length - 1; i >= 1; i--) {
        if (functionVarargType.acceptsRepeatedArguments()) {
          varargs.add(functionVarargType.getArgumentType());
          anyVararg = true;
        } else {
          varargs.add(functionVarargType.getArgumentType());
          functionVarargType = functionVarargType.getReturnType();
        }
      }
      
      if (anyVararg) {
        functionVarargType = functionVarargType.getReturnType();
        for (int i = varargs.size() - 1; i >= 0; i--) {
          functionVarargType = functionVarargType.addArgument(varargs.get(i));
        }
        functionType = functionVarargType;
        expressionTypes.put(functionIndex, functionType);
      }
      
      constraints.addEquality(functionType, supertype);
      expressionTypes.put(index, returnType);
    }
  }
}
