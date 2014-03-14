package com.jayantkrish.jklol.ccg.lambda;

import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class LambdaExpression extends AbstractExpression {
  private static final long serialVersionUID = 1L;

  private final List<ConstantExpression> argumentVariables;
  private final List<Type> argumentTypes;

  private final Expression body;

  public LambdaExpression(List<ConstantExpression> argumentVariables, Expression body) {
    this.argumentVariables = ImmutableList.copyOf(argumentVariables);
    this.argumentTypes = null;
    this.body = Preconditions.checkNotNull(body);
  }

  public LambdaExpression(List<ConstantExpression> argumentVariables,
      List<Type> argumentTypes, Expression body) {
    this.argumentVariables = ImmutableList.copyOf(argumentVariables);
    this.argumentTypes = ImmutableList.copyOf(argumentTypes);
    Preconditions.checkArgument(argumentTypes.size() == argumentVariables.size());

    this.body = Preconditions.checkNotNull(body);
  }

  /**
   * Returns the body of the function, i.e., the code that gets
   * executed when the function is invoked.
   * 
   * @return
   */
  public Expression getBody() {
    return body;
  }
  
  /**
   * Gets the variables which are arguments to the function.
   * 
   * @return
   */
  public List<ConstantExpression> getArguments() {
    return argumentVariables;
  }

  public List<Type> getArgumentTypes() {
    return argumentTypes;
  }

  public Expression reduce(List<Expression> argumentValues) {
    Preconditions.checkArgument(argumentValues.size() <= argumentVariables.size(), 
        "Too many arguments. Expected %s, got %s. This expression: %s", argumentVariables,
        argumentValues, this);

    Expression substitutedBody = body;
    for (int i = 0; i < argumentValues.size(); i++) {
      Expression argumentValue = argumentValues.get(i);
      Set<ConstantExpression> argumentFreeVars = argumentValue.getFreeVariables();
      Set<ConstantExpression> boundVars = substitutedBody.getBoundVariables();

      for (ConstantExpression boundVar : boundVars) {
        if (argumentFreeVars.contains(boundVar)) {
          // Rename the bound variable to avoid a variable name collision.
          ConstantExpression newBoundVarName = ConstantExpression.generateUniqueVariable();
          substitutedBody = substitutedBody.renameVariable(boundVar, newBoundVarName);
        }
      }
      substitutedBody = substitutedBody.substitute(argumentVariables.get(i), argumentValue);
    }

    if (argumentValues.size() == argumentVariables.size()) {
      return substitutedBody;
    } else {
      return new LambdaExpression(argumentVariables.subList(argumentValues.size(), argumentVariables.size()), substitutedBody);
    }
  }

  public Expression reduceArgument(ConstantExpression argumentVariable, Expression value) {
    Preconditions.checkArgument(argumentVariables.contains(argumentVariable));

    List<ConstantExpression> remainingArguments = Lists.newArrayList();
    Expression substitutedBody = body;
    for (int i = 0; i < argumentVariables.size(); i++) {
      if (argumentVariables.get(i).equals(argumentVariable)) {
        Set<ConstantExpression> argumentFreeVars = value.getFreeVariables();
        Set<ConstantExpression> boundVars = substitutedBody.getBoundVariables();

        for (ConstantExpression boundVar : boundVars) {
          if (argumentFreeVars.contains(boundVar)) {
            // Rename the bound variable to avoid a variable name collision.
            ConstantExpression newBoundVarName = ConstantExpression.generateUniqueVariable();
            substitutedBody = substitutedBody.renameVariable(boundVar, newBoundVarName);
          }
        }

        substitutedBody = substitutedBody.substitute(argumentVariables.get(i), value);
      } else {
        remainingArguments.add(argumentVariables.get(i));
      }
    }

    if (remainingArguments.size() > 0) {
      return new LambdaExpression(remainingArguments, substitutedBody);
    } else {
      return substitutedBody;
    }
  }

  @Override
  public void getFreeVariables(Set<ConstantExpression> accumulator) {
    body.getFreeVariables(accumulator);
    accumulator.removeAll(argumentVariables);
  }
  
  @Override
  public void getBoundVariables(Set<ConstantExpression> accumulator) {
    body.getBoundVariables(accumulator);
    accumulator.addAll(argumentVariables);
  }
  
  @Override
  public List<ConstantExpression> getLocallyBoundVariables() {
    return getArguments();
  }
  
  @Override
  public LambdaExpression renameVariable(ConstantExpression variable, ConstantExpression replacement) {
    List<ConstantExpression> substitutedArguments = Lists.newArrayList();
    for (ConstantExpression boundVariable : argumentVariables) {
      substitutedArguments.add(boundVariable.renameVariable(variable, replacement));
    }
    Expression substitutedBody = body.renameVariable(variable, replacement);

    return new LambdaExpression(substitutedArguments, substitutedBody);
  }

  @Override
  public Expression substitute(ConstantExpression constant, Expression replacement) {
    if (!argumentVariables.contains(constant)) {
      Expression substitution = body.substitute(constant, replacement);
      return new LambdaExpression(argumentVariables, substitution);
    } else {
      return this;
    }
  }

  @Override
  public Expression simplify() {
    Expression simplifiedBody = body.simplify();
    if (simplifiedBody instanceof LambdaExpression) {
      LambdaExpression bodyAsLambda = (LambdaExpression) simplifiedBody;
      List<ConstantExpression> argumentList = Lists.newArrayList(argumentVariables);
      argumentList.addAll(bodyAsLambda.getArguments());
      
      return new LambdaExpression(argumentList, bodyAsLambda.getBody());
    }
    return new LambdaExpression(argumentVariables, simplifiedBody);
  }

  @Override
  public boolean functionallyEquals(Expression expression) {
    if (expression instanceof LambdaExpression) {
      LambdaExpression other = (LambdaExpression) expression;
      List<ConstantExpression> otherArguments = other.getArguments();
      if (otherArguments.size() == argumentVariables.size()) {
        // Functions are equal if both 
        LambdaExpression otherSubstituted = other;
        LambdaExpression substituted = this;
        for (int i = 0; i < otherArguments.size(); i++) {
          ConstantExpression newVar = ConstantExpression.generateUniqueVariable();
          otherSubstituted = otherSubstituted.renameVariable(otherArguments.get(i), newVar);
          substituted = substituted.renameVariable(argumentVariables.get(i), newVar);
        }

        return substituted.body.functionallyEquals(otherSubstituted.body);
      }
    }
    return false;
  }

  @Override
  public Type getType(TypeContext context) {
    if (argumentTypes == null) {
      return null;
    }

    List<String> argumentNames = Lists.newArrayList();
    for (ConstantExpression var : argumentVariables) {
      argumentNames.add(var.getName());
    }

    TypeContext boundContext = context.bindNames(argumentNames, argumentTypes);
    Type type = body.getType(boundContext);
    for (int i = argumentTypes.size() - 1; i >= 0; i--) {
      type = Type.createFunctional(argumentTypes.get(i), type);
    }
    return type;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("(lambda");
    for (Expression argument : argumentVariables) {
      sb.append(" ");
      sb.append(argument.toString());
    }

    sb.append(" ");
    sb.append(body.toString());
    sb.append(")");
    return sb.toString();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((argumentVariables == null) ? 0 : argumentVariables.hashCode());
    result = prime * result + ((body == null) ? 0 : body.hashCode());
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
    LambdaExpression other = (LambdaExpression) obj;
    if (argumentVariables == null) {
      if (other.argumentVariables != null)
        return false;
    } else if (!argumentVariables.equals(other.argumentVariables))
      return false;
    if (body == null) {
      if (other.body != null)
        return false;
    } else if (!body.equals(other.body))
      return false;
    return true;
  }
}
