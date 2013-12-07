package com.jayantkrish.jklol.ccg.lambda;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.util.IntegerArrayIterator;

/**
 * Universal quantifier, where each bound variable takes on a
 * restricted set of restrictions.
 * 
 * @author jayantk
 */
public class ForAllExpression extends AbstractExpression {
  private static final long serialVersionUID = 1L;

  private final List<ConstantExpression> boundVariables;
  private final List<Expression> restrictions;

  private final Expression body;

  public ForAllExpression(List<ConstantExpression> boundVariables, 
      List<Expression> restrictions, Expression body) {
    this.boundVariables = ImmutableList.copyOf(boundVariables);
    this.restrictions = ImmutableList.copyOf(restrictions);
    this.body = Preconditions.checkNotNull(body);
  }
  
  public Expression getBody() {
    return body;
  }
  
  public List<Expression> getRestrictions() {
    return restrictions;
  }
  
  public Expression expandQuantifier() {
    // Generate unique names for the bound variables to avoid
    // accidental substitutions. 
    List<ConstantExpression> newBoundVariables = ConstantExpression.generateUniqueVariables(boundVariables.size());
    Expression baseClause = body.renameVariables(boundVariables, newBoundVariables).freshenVariables(body.getBoundVariables());
    int[] bindingSizes = new int[boundVariables.size()];
    for (int i = 0; i < boundVariables.size(); i++) {
      CommutativeOperator set = (CommutativeOperator) restrictions.get(i);
      bindingSizes[i] = set.getArguments().size();
    }
    
    // Systematically substitute every possible value for every
    // bound variable to create a collection of clauses. This expression
    // is equivalent to the conjunction of these clauses.
    Iterator<int[]> bindingIter = new IntegerArrayIterator(bindingSizes, new int[0]);
    List<Expression> boundClauses = Lists.newArrayList();
    while (bindingIter.hasNext()) {
      int[] bindingIndexes = bindingIter.next();
      
      Expression result = baseClause;
      for (int j = 0; j < bindingIndexes.length; j++) {
        ConstantExpression boundVar = newBoundVariables.get(j);
        CommutativeOperator set = (CommutativeOperator) restrictions.get(j);
        
        result = result.substitute(boundVar, set.getArguments().get(bindingIndexes[j])).simplify();
        if (result instanceof ForAllExpression) {
          result = ((ForAllExpression) result).expandQuantifier().simplify();
        }
      }
      boundClauses.add(result.simplify());
    }

    Expression clauseConjunction = (new CommutativeOperator(new ConstantExpression("and"), boundClauses));
    Preconditions.checkState(!(clauseConjunction.simplify() instanceof ForAllExpression));
    return clauseConjunction;
  }

  @Override
  public void getFreeVariables(Set<ConstantExpression> accumulator) {
    body.getFreeVariables(accumulator);
    for (Expression restriction : restrictions) {
      restriction.getFreeVariables(accumulator);
    }

    accumulator.removeAll(boundVariables);
  }

  @Override
  public void getBoundVariables(Set<ConstantExpression> accumulator) {
    body.getBoundVariables(accumulator);
    for (Expression restriction : restrictions) {
      restriction.getBoundVariables(accumulator);
    }

    accumulator.addAll(boundVariables);
  }
  
  @Override
  public List<ConstantExpression> getLocallyBoundVariables() {
    return boundVariables;
  }

  @Override
  public Expression renameVariable(ConstantExpression variable, ConstantExpression replacement) {
    List<ConstantExpression> substitutedBoundVariables = Lists.newArrayList();
    List<Expression> substitutedValues = Lists.newArrayList();
    for (int i = 0; i < boundVariables.size(); i++) {
      ConstantExpression boundVariable = boundVariables.get(i);
      substitutedBoundVariables.add(boundVariable.renameVariable(variable, replacement));

      substitutedValues.add(restrictions.get(i).renameVariable(variable, replacement));
    }
    Expression substitutedBody = body.renameVariable(variable, replacement);
    ForAllExpression result = new ForAllExpression(substitutedBoundVariables, substitutedValues, substitutedBody);
    return result;
  }

  @Override
  public Expression substitute(ConstantExpression constant, Expression replacement) {
    if (!boundVariables.contains(constant)) {
      List<Expression> substitutedRestrictions = Lists.newArrayList();
      for (Expression expr : restrictions) {
        substitutedRestrictions.add(expr.substitute(constant, replacement));
      }

      return new ForAllExpression(boundVariables, substitutedRestrictions, body.substitute(constant, replacement));
    } else {
      return this;
    }
  }

  @Override
  public Expression simplify() {
    List<Expression> simplifiedRestrictions = Lists.newArrayList();
    for (int i = 0; i < restrictions.size(); i++) {
      simplifiedRestrictions.add(restrictions.get(i).simplify());
    }

    Expression simplifiedBody = body.simplify();
    if (simplifiedBody instanceof ForAllExpression) {
      ForAllExpression forall = (ForAllExpression) simplifiedBody;
      ForAllExpression relabeled = (ForAllExpression) forall.freshenVariables(boundVariables);

      List<ConstantExpression> newBoundVariables = Lists.newArrayList(boundVariables);
      newBoundVariables.addAll(relabeled.getLocallyBoundVariables());
      
      List<Expression> newRestrictions = Lists.newArrayList(restrictions);
      newRestrictions.addAll(relabeled.getRestrictions());

      return new ForAllExpression(newBoundVariables, newRestrictions, relabeled.getBody());
    }

    return new ForAllExpression(boundVariables, simplifiedRestrictions, body.simplify());
  }

  @Override
  public boolean functionallyEquals(Expression expression) {
    if (expression instanceof ForAllExpression) {
      ForAllExpression other = (ForAllExpression) expression;
      List<ConstantExpression> otherBoundVars = Lists.newArrayList(other.boundVariables);
      List<Expression> otherRestrictions = Lists.newArrayList(other.getRestrictions());
      if (otherBoundVars.size() == boundVariables.size()) {
        Preconditions.checkState(otherBoundVars.size() == 1, "For all quantifier with multiple vars not yet implemented.");
        if (!otherRestrictions.get(0).functionallyEquals(restrictions.get(0))) {
          return false;
        }

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
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("(forall");
    for (int i = 0; i < boundVariables.size(); i++) {
      sb.append(" (");
      sb.append(Joiner.on(" ").join(boundVariables.get(i), restrictions.get(i)));
      sb.append(")");
    }
    sb.append(" ");
    sb.append(body);
    sb.append(")");
    return sb.toString();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((body == null) ? 0 : body.hashCode());
    result = prime * result + ((boundVariables == null) ? 0 : boundVariables.hashCode());
    result = prime * result + ((restrictions == null) ? 0 : restrictions.hashCode());
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
    ForAllExpression other = (ForAllExpression) obj;
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
    if (restrictions == null) {
      if (other.restrictions != null)
        return false;
    } else if (!restrictions.equals(other.restrictions))
      return false;
    return true;
  }
}
