package com.jayantkrish.jklol.ccg.lambda2;

import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.ccg.lambda.Type;
import com.jayantkrish.jklol.ccg.lambda.TypeDeclaration;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis.Scope;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis.ScopeSet;

public class TypeInference {

  private final Expression2 expression;
  private final TypeDeclaration typeDeclaration;
  
  private Map<Integer, Type> expressionTypes;
  private ScopeSet scopes;
  private int typeVarCounter = 0;
  private ConstraintSet rootConstraints = null;
  private ConstraintSet solved = null;
  
  public TypeInference(Expression2 expression, TypeDeclaration typeDeclaration) {
    this.expression = Preconditions.checkNotNull(expression);
    this.typeDeclaration = Preconditions.checkNotNull(typeDeclaration);
  }
  
  public Map<Integer, Type> getExpressionTypes() {
    return expressionTypes;
  }

  public void infer() {
    scopes = StaticAnalysis.getScopes(expression);
    expressionTypes = Maps.newHashMap();
    rootConstraints = ConstraintSet.empty();
    for (Scope scope : scopes.getScopes()) {
      for (String variable : scope.getBoundVariables()) {
        int location = scope.getBindingIndex(variable);
        if (!expressionTypes.containsKey(location)) {
          expressionTypes.put(location, rootConstraints.getFreshTypeVar());
        }
      }
    }

    populateExpressionTypes(0);
    // TODO: root type.
    System.out.println("root");
    System.out.println(rootConstraints);
    ConstraintSet atomicConstraints = rootConstraints.makeAtomic(typeVarCounter);
    System.out.println("atomic");
    System.out.println(atomicConstraints);

    solved = atomicConstraints.solveAtomic(typeDeclaration);
    System.out.println("solved");
    System.out.println(solved);

    for (int k : expressionTypes.keySet()) {
      expressionTypes.put(k, expressionTypes.get(k).substitute(solved.getBindings()));
    }
  }

  public ConstraintSet getConstraints() {
    return rootConstraints;
  }

  public ConstraintSet getSolvedConstraints() {
    return solved;
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
            Type fresh = rootConstraints.getFreshTypeVar();
            while (usedTypeVars.contains(fresh.getAtomicTypeVar())) {
              fresh = rootConstraints.getFreshTypeVar();
            }
            substitutions.put(var, fresh);
          }
          type = type.substitute(substitutions);
        }
        
        expressionTypes.put(index, type);

        /*
        if (expression.getParentExpressionIndex(index) == index - 1) {
          // Subexpression is the first expression in an application.
          // Don't upcast it.
          
        } else {
          List<SubtypeConstraint> constraints = Lists.newArrayList();
          Type supertype = upcast(type, constraints, true, false);
          expressionTypes.put(index, supertype);
          return new ConstraintSet(Lists.newArrayList(), constraints, Maps.newHashMap(), true);
        }
        */
      } else {
        // Get the type variable for this binding and add it here.
        Type boundType = expressionTypes.get(bindingIndex);
        expressionTypes.put(index, boundType);
        /*
        List<SubtypeConstraint> constraints = Lists.newArrayList();
        Type supertype = upcast(boundType, constraints, true, true);
        expressionTypes.put(index, supertype);
        ConstraintSet constraintSet = new ConstraintSet(Lists.newArrayList(),
            constraints, Maps.newHashMap(), true);
        // System.out.println(index);
        // System.out.println(constraintSet);
        return constraintSet; 
        */
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

      Type returnType = rootConstraints.getFreshTypeVar();
      Type supertype = returnType;
      for (int i = childIndexes.length - 1; i >= 1; i--) {
        populateExpressionTypes(childIndexes[i]);
        
        Type argType = expressionTypes.get(childIndexes[i]);
        Type argSupertype = rootConstraints.upcast(argType, true);

        supertype = supertype.addArgument(argSupertype);
      }
      // System.out.println(subexpression);

      // Hack to handle types with vararg parameters.
      if (functionType.acceptsRepeatedArguments()) {
        Type instantiated = functionType.getReturnType();
        for (int i = childIndexes.length - 1; i >= 1; i--) {
          instantiated = instantiated.addArgument(functionType.getArgumentType());
        }
        functionType = instantiated;
        expressionTypes.put(functionIndex, functionType);
      }

      rootConstraints.addEquality(functionType, supertype);
      expressionTypes.put(index, returnType);
    }
  }
}
