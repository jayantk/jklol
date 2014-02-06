package com.jayantkrish.jklol.ccg.lambda;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * A LISP-style S-expression representing a lambda calculus
 * statement for a second-order logic. The logic includes
 * both existential and universal quantifiers, and lambda
 * expressions for defining functions. 
 *  
 * @author jayantk
 */
public interface Expression extends Serializable {

  /**
   * Gets the set of unbound variables in this expression. Free
   * variables get their value from the environment they are evaluated
   * in.
   * 
   * @return
   */
  Set<ConstantExpression> getFreeVariables();

  /**
   * Gets the set of unbound variables in this expression and 
   * adds them to {@code accumulator}. Also see
   * {@link #getFreeVariables()}.
   *  
   * @param accumulator
   */
  void getFreeVariables(Set<ConstantExpression> accumulator);

  /**
   * Gets the set of bound variables in this expression. The value of
   * a bound variable is set by a component of this expression, i.e.,
   * it does not depend on the environment it is evaluated in. Bound
   * variables are created (for instance) by lambda expressions and
   * quantifiers.
   * 
   * @return
   */
  Set<ConstantExpression> getBoundVariables();

  void getBoundVariables(Set<ConstantExpression> accumulator);
  
  /**
   * Gets variables bound by the root of this expression, not including
   * any variables bound by subexpressions.
   * 
   * @return
   */
  List<ConstantExpression> getLocallyBoundVariables();

  /**
   * Creates a new expression by replacing every occurrence of
   * {@code variable} in this expression with {@code replacement}. 
   * 
   * @param variable
   * @param replacement
   * @return
   */
  Expression renameVariable(ConstantExpression variable, ConstantExpression replacement);

  /**
   * Creates a new expression by replacing every variable from
   * {@code variables} in this expression with the variable at
   * the corresponding index in {@code replacements}. 
   * 
   * @param variable
   * @param replacement
   * @return
   */
  Expression renameVariables(List<ConstantExpression> variables, List<ConstantExpression> replacements);
  
  /**
   * Replaces every variable in {@code boundVariables} with a fresh (unique)
   * variable, if that variable is bound.
   *  
   * @param boundVariables
   * @return
   */
  Expression freshenVariables(Collection<ConstantExpression> boundVariables);

  /**
   * Replaces the free variable named {@code constant} by
   * {@code replacement}.
   * 
   * @param constant
   * @param replacement
   * @return
   */
  Expression substitute(ConstantExpression constant, Expression replacement);

  /**
   * Simplifies the expression by performing lambda reduction and
   * grouping quantifiers. The returned expression expresses the same
   * function as the original expression.
   * 
   * @return
   */
  Expression simplify();

  /**
   * Returns true if {@code this} and {@code expression} have the same
   * truth conditions, i.e., they express the same function.
   * Functional equality is a symmetric, transitive relation between
   * expressions.
   * <p>
   * This method expects both expressions being compared to be
   * simplified.
   * 
   * @param expression
   * @return
   */
  boolean functionallyEquals(Expression expression);

  @Override
  int hashCode();

  @Override
  boolean equals(Object o);
}
