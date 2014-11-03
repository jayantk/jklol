package com.jayantkrish.jklol.ccg.lambda;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * Represents a commutative mathematical operator, such as "and" or
 * "or".
 * 
 * @author jayantk
 */
public class CommutativeOperator extends AbstractExpression {
  private static final long serialVersionUID = 1L;

  private final ConstantExpression operatorName;
  private final List<Expression> arguments;

  public CommutativeOperator(ConstantExpression function, List<Expression> arguments) {
    this.operatorName = Preconditions.checkNotNull(function);
    this.arguments = ImmutableList.copyOf(arguments);
  }

  public ConstantExpression getOperatorName() {
    return operatorName;
  }

  public List<Expression> getArguments() {
    return arguments;
  }

  @Override
  public void getFreeVariables(Set<ConstantExpression> accumulator) {
    operatorName.getFreeVariables(accumulator);
    for (Expression argument : arguments) {
      argument.getFreeVariables(accumulator);
    }
  }

  @Override
  public void getBoundVariables(Set<ConstantExpression> accumulator) {
    operatorName.getBoundVariables(accumulator);
    for (Expression argument : arguments) {
      argument.getBoundVariables(accumulator);
    }
  }
  
  @Override
  public List<ConstantExpression> getLocallyBoundVariables() {
    return Collections.emptyList();
  }

  @Override
  public Expression renameVariable(ConstantExpression variable, ConstantExpression replacement) {
    List<Expression> substituted = Lists.newArrayList();
    for (Expression subexpression : arguments) {
      substituted.add(subexpression.renameVariable(variable, replacement));
    }
    return new CommutativeOperator(operatorName.renameVariable(variable, replacement), substituted);
  }

  @Override
  public Expression substitute(ConstantExpression constant, Expression replacement) {
    List<Expression> substituted = Lists.newArrayList();
    for (Expression subexpression : arguments) {
      substituted.add(subexpression.substitute(constant, replacement));
    }
    return new CommutativeOperator(operatorName, substituted);
  }

  @Override
  public Expression simplify() {
    List<Expression> simplified = Lists.newArrayList();
    List<QuantifierExpression> wrappingQuantifiers = Lists.newArrayList();
    List<ForAllExpression> wrappingUniversals = Lists.newArrayList();
    for (Expression subexpression : arguments) {
      Expression simplifiedArgument = subexpression.simplify();
      
      // Push quantifiers outside of logical operators.
      if (simplifiedArgument instanceof QuantifierExpression) {
        QuantifierExpression quantifierArgument = ((QuantifierExpression) simplifiedArgument);
        // Avoid variable name collisions.
        quantifierArgument = (QuantifierExpression) quantifierArgument.freshenVariables(quantifierArgument.getLocallyBoundVariables());
        wrappingQuantifiers.add(quantifierArgument);
        simplified.add(quantifierArgument.getBody());
      } else if (simplifiedArgument instanceof ForAllExpression) {
        ForAllExpression forAll = ((ForAllExpression) simplifiedArgument);
        // Avoid variable name collisions.
        forAll = (ForAllExpression) forAll.freshenVariables(forAll.getBoundVariables());
        wrappingUniversals.add(forAll);
        simplified.add(forAll.getBody());
      } else {
        simplified.add(simplifiedArgument);
      }
    }

    List<Expression> resultClauses = Lists.newArrayList();
    for (Expression subexpression : simplified) {
      if (subexpression instanceof CommutativeOperator) {
        CommutativeOperator commutative = (CommutativeOperator) subexpression;
        if (commutative.getOperatorName().equals(getOperatorName())) {
          resultClauses.addAll(commutative.getArguments());
        } else {
          resultClauses.add(commutative);
        }
      } else {
        resultClauses.add(subexpression);
      }
    }

    Expression result = new CommutativeOperator(operatorName, resultClauses);
    // Wrap the result with the appropriate quantifiers.
    
    for (QuantifierExpression quantifier : wrappingQuantifiers) {
      result = new QuantifierExpression(quantifier.getQuantifierName(),
          quantifier.getLocallyBoundVariables(), result);
    }

    for (ForAllExpression quantifier : wrappingUniversals) {
      result = new ForAllExpression(quantifier.getLocallyBoundVariables(), 
          quantifier.getRestrictions(), result);
    }
    
    if (wrappingQuantifiers.size() > 0 || wrappingUniversals.size() > 0) {
      return result.simplify();
    } else {
      return result;
    }
  }
  
  @Override
  public boolean functionallyEquals(Expression other) {
    if (other instanceof CommutativeOperator) {
      CommutativeOperator otherOp = (CommutativeOperator) other;
      if (otherOp.getOperatorName().functionallyEquals(operatorName)) {
        // The order of the arguments to this operator doesn't matter.
        List<Expression> otherArguments = otherOp.getArguments();
        if (arguments.size() != otherArguments.size()) {
          // Can't find a mapping between differently sized argument sets.
          return false;
        }

        boolean[] otherArgumentUsed = new boolean[otherArguments.size()];
        Arrays.fill(otherArgumentUsed, false);
        for (Expression argument : arguments) {
          boolean foundArg = false;

          for (int i = 0; i < otherArguments.size(); i++) {
            if (otherArgumentUsed[i]) {
              continue;
            }
            Expression otherArgument = otherArguments.get(i);
            if (argument.functionallyEquals(otherArgument)) {
              otherArgumentUsed[i] = true;
              foundArg = true;
              break;
            }
          }
          
          if (!foundArg) {
            return false;
          }
        }
        // All arguments were matched with a unique otherArgument.
        return true;
      }
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
    sb.append(getOperatorName());
    for (Expression argument : getArguments()) {
      sb.append(" ");
      sb.append(argument.toString());
    }
    sb.append(")");
    return sb.toString();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((arguments == null) ? 0 : arguments.hashCode());
    result = prime * result + ((operatorName == null) ? 0 : operatorName.hashCode());
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
    CommutativeOperator other = (CommutativeOperator) obj;
    if (arguments == null) {
      if (other.arguments != null)
        return false;
    } else if (!arguments.equals(other.arguments))
      return false;
    if (operatorName == null) {
      if (other.operatorName != null)
        return false;
    } else if (!operatorName.equals(other.operatorName))
      return false;
    return true;
  }
}
