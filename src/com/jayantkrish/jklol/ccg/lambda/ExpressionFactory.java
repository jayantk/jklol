package com.jayantkrish.jklol.ccg.lambda;

import java.util.List;

/**
 * Interface for creating special forms of expressions during
 * parsing with {@link ExpressionParser}. Special forms are
 * defined by using keywords as the first element of an S-expression
 * (e.g., {@code (lambda x (f x))}). 
 *
 * @author jayantk
 */
public interface ExpressionFactory<T> {

  public T createTokenExpression(String token);

  public T createExpression(List<T> subexpressions);
}
