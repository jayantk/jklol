package com.jayantkrish.jklol.ccg.lambda2;

import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.ccg.lambda.Type;
import com.jayantkrish.jklol.ccg.lambda.TypeDeclaration;
import com.jayantkrish.jklol.ccg.lambda2.ConstraintSet.SubtypeConstraint;
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
    typeVarCounter = 0;
    for (Scope scope : scopes.getScopes()) {
      for (String variable : scope.getBoundVariables()) {
        int location = scope.getBindingIndex(variable);
        if (!expressionTypes.containsKey(location)) {
          expressionTypes.put(location, getFreshTypeVar());
        }
      }
    }

    rootConstraints = populateExpressionTypes(0);
    // TODO: root type.
    System.out.println("Making atomic:");
    ConstraintSet atomicConstraints = rootConstraints.makeAtomic(typeVarCounter);
    System.out.println("solving:");
    solved = atomicConstraints.solveAtomic(typeDeclaration);

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

  public Type getFreshTypeVar() {
    return Type.createTypeVariable(typeVarCounter++);
  }

  private ConstraintSet populateExpressionTypes(int index) {
    Expression2 subexpression = expression.getSubexpression(index);
    if (subexpression.isConstant()) {
      Scope scope = scopes.getScope(index);
      int bindingIndex = scope.getBindingIndex(subexpression.getConstant());
      if (bindingIndex == -1) {
        // Get the type of this constant if it is declared
        // and it's not a lambda variable.
        // TODO: freshen any type variables in the retrieved type.
        Type type = typeDeclaration.getType(subexpression.getConstant());
        expressionTypes.put(index, type);
      } else {
        // Get the type variable for this binding and add it here.
        Type boundType = expressionTypes.get(bindingIndex);
        expressionTypes.put(index, boundType);
      }
      return ConstraintSet.EMPTY;
    } else if (StaticAnalysis.isLambda(subexpression)) {
      int[] childIndexes = expression.getChildIndexes(index);
      int bodyIndex = childIndexes[childIndexes.length - 1];
      int[] argIndexes = expression.getChildIndexes(childIndexes[1]);

      ConstraintSet bodyConstraints = populateExpressionTypes(bodyIndex);
      Type lambdaType = expressionTypes.get(bodyIndex);
      for (int i = argIndexes.length - 1; i >= 0; i--) {
        populateExpressionTypes(argIndexes[i]);
        lambdaType = lambdaType.addArgument(expressionTypes.get(argIndexes[i]));
      }

      expressionTypes.put(index, lambdaType);
      return bodyConstraints;
    } else {
      // Function application
      int[] childIndexes = expression.getChildIndexes(index);
      int functionIndex = childIndexes[0];

      ConstraintSet constraints = populateExpressionTypes(functionIndex);
      Type functionType = expressionTypes.get(functionIndex);

      Type returnType = getFreshTypeVar();
      Type supertype = returnType;
      for (int i = childIndexes.length - 1; i >= 1; i--) {
        ConstraintSet argConstraints = populateExpressionTypes(childIndexes[i]);
        constraints = constraints.union(argConstraints);
        
        Type argType = expressionTypes.get(childIndexes[i]);
        supertype = supertype.addArgument(argType);
      }

      // Hack to handle types with vararg parameters.
      if (functionType.acceptsRepeatedArguments()) {
        Type instantiated = functionType.getReturnType();
        for (int i = childIndexes.length - 1; i >= 1; i--) {
          instantiated = instantiated.addArgument(functionType.getArgumentType());
        }
        functionType = instantiated;
        expressionTypes.put(functionIndex, functionType);
      }

      constraints = constraints.add(new SubtypeConstraint(functionType, supertype));
      // constraints = constraints.solveIncremental();
      expressionTypes.put(index, returnType.substitute(constraints.getBindings()));
      return constraints;
    }
  }
}
