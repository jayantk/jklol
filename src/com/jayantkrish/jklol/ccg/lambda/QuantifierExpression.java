package com.jayantkrish.jklol.ccg.lambda;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;

public class QuantifierExpression extends AbstractExpression {
  private static final long serialVersionUID = 1L;

  private final String quantifierName;
  private final List<ConstantExpression> boundVariables;
  private final Expression body;

  public QuantifierExpression(String quantifierName, List<ConstantExpression> boundVariables,
      Expression body) {
    this.quantifierName = quantifierName;
    this.boundVariables = Lists.newArrayList(boundVariables);
    Collections.sort(this.boundVariables);
    this.body = Preconditions.checkNotNull(body);
  }
  
  public String getQuantifierName() {
    return quantifierName;
  }

  /**
   * Returns the body of the statement, i.e., the portion of the
   * statement over which the quantifier the quantifier has scope.
   *
   * @return
   */
  public Expression getBody() {
    return body;
  }

  @Override
  public void getFreeVariables(Set<ConstantExpression> accumulator) {
    body.getFreeVariables(accumulator);
    accumulator.removeAll(boundVariables);
  }

  @Override
  public void getBoundVariables(Set<ConstantExpression> accumulator) {
    body.getBoundVariables(accumulator);
    accumulator.addAll(boundVariables);
  }
  
  @Override
  public List<ConstantExpression> getLocallyBoundVariables() {
    return boundVariables;
  }

  @Override
  public QuantifierExpression renameVariable(ConstantExpression variable, ConstantExpression replacement) {
    List<ConstantExpression> substitutedBoundVariables = Lists.newArrayList();
    for (ConstantExpression boundVariable : boundVariables) {
      substitutedBoundVariables.add(boundVariable.renameVariable(variable, replacement));
    }
    Expression substitutedBody = body.renameVariable(variable, replacement);

    return new QuantifierExpression(quantifierName, substitutedBoundVariables, substitutedBody);
  }

  @Override
  public Expression substitute(ConstantExpression constant, Expression replacement) {
    if (!boundVariables.contains(constant)) {
      Expression substitution = body.substitute(constant, replacement);
      return new QuantifierExpression(quantifierName, boundVariables, substitution);
    } else {
      return this;
    }
  }

  @Override
  public Expression simplify() {
    Expression simplifiedBody = body.simplify();
    
    // Only free variables in the body need to be quantified.
    // The other variables aren't referenced and so can be deleted.
    List<ConstantExpression> simplifiedBoundVariables = Lists.newArrayList(boundVariables);
    simplifiedBoundVariables.retainAll(simplifiedBody.getFreeVariables());

    // No quantified variables means this expression is redundant.
    if (simplifiedBoundVariables.size() == 0) {
      return simplifiedBody;
    }

    if (simplifiedBody instanceof QuantifierExpression) {
      QuantifierExpression quant = (QuantifierExpression) simplifiedBody;
      if (quant.getQuantifierName().equals(quantifierName)) {
        // Group like quantifiers.
        QuantifierExpression relabeled = (QuantifierExpression) quant.freshenVariables(simplifiedBoundVariables);
        
        List<ConstantExpression> newBoundVariables = Lists.newArrayList(relabeled.getLocallyBoundVariables());
        newBoundVariables.addAll(simplifiedBoundVariables);
        return new QuantifierExpression(quantifierName, newBoundVariables, relabeled.getBody());
      }
    } else if (simplifiedBody instanceof ForAllExpression) {
      ForAllExpression forall = (ForAllExpression) simplifiedBody;
      forall = (ForAllExpression) forall.freshenVariables(simplifiedBoundVariables);
      
      Expression newBody = new QuantifierExpression(quantifierName, simplifiedBoundVariables, forall.getBody());
      return new ForAllExpression(forall.getLocallyBoundVariables(), forall.getRestrictions(), newBody);
    } 

    return new QuantifierExpression(quantifierName, simplifiedBoundVariables, simplifiedBody);
  }

  @Override
  public boolean functionallyEquals(Expression expression) {
    if (expression instanceof QuantifierExpression) {
      QuantifierExpression other = (QuantifierExpression) expression;
      List<ConstantExpression> otherBoundVars = Lists.newArrayList(other.boundVariables);
      if (otherBoundVars.size() == boundVariables.size() && other.getQuantifierName().equals(quantifierName)) {
        // The order of variables and their names do not matter in a quantified expression.
        // Quantifiers are functionally equal if there exists a mapping between the quantified variables
        // such that the bodies are functionally equal.
        List<ConstantExpression> targetVars = ConstantExpression.generateUniqueVariables(boundVariables.size());
        Expression otherBodySubstituted = other.getBody().renameVariables(otherBoundVars, targetVars);

        for (List<ConstantExpression> permutedTargets : Collections2.permutations(targetVars)) {
          Expression myBodySubstituted = body.renameVariables(boundVariables, permutedTargets);
          if (myBodySubstituted.functionallyEquals(otherBodySubstituted)) {
            return true;
          }
        }
      }
      return false;
    }
    return false;
  }

  @Override
  public Type getType(TypeContext context) {
    return null;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("(");
    sb.append(quantifierName);
    for (Expression argument : boundVariables) {
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
    result = prime * result + ((body == null) ? 0 : body.hashCode());
    result = prime * result + ((boundVariables == null) ? 0 : boundVariables.hashCode());
    result = prime * result + ((quantifierName == null) ? 0 : quantifierName.hashCode());
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
    QuantifierExpression other = (QuantifierExpression) obj;
    if (body == null) {
      if (other.body != null)
        return false;
    } else if (!body.equals(other.body))
      return false;
    if (boundVariables == null) {
      if (other.boundVariables != null)
        return false;
    } else if (!boundVariables.equals(other.boundVariables))
      return false;
    if (quantifierName == null) {
      if (other.quantifierName != null)
        return false;
    } else if (!quantifierName.equals(other.quantifierName))
      return false;
    return true;
  }
}
