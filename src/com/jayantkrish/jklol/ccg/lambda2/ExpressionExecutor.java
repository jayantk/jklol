package com.jayantkrish.jklol.ccg.lambda2;

import java.util.List;

import com.google.common.base.Optional;

/**
 * Interface for executing expressions, i.e., logical forms,
 * to obtain denotations. Execution can optionally occur
 * within a context that influences execution and the
 * returned denotation. The context is typically a database
 * or world.
 * 
 * @author jayantk
 *
 */
public interface ExpressionExecutor {

  /**
   * Execute {@code lf} with a {@code null} context.
   * If execution causes an error, this method will not
   * intercept the resulting {@link EvalError}.  
   * 
   * @param lf
   * @return
   */
  public Object evaluate(Expression2 lf);
  
  public Object evaluate(Expression2 lf, Object context);
  
  public Optional<Object> evaluateSilent(Expression2 lf);

  /**
   * Execute {@code lf} within {@code context}, silencing
   * errors. If an error occurs, returns an {@code Optional}
   * that does not contain a value.
   *  
   * @param lf
   * @param context
   * @return
   */
  public Optional<Object> evaluateSilent(Expression2 lf, Object context);

  /**
   * Executes {@code funcLf} applied to the values {@code args}.
   * If execution causes an error, this method will not
   * intercept the resulting {@link EvalError}.
   *  
   * @param funcLf
   * @param args
   * @return
   */
  public Object apply(Expression2 funcLf, List<Object> args);

  public Object apply(Expression2 funcLf, Object context, List<Object> args);
  
  public Optional<Object> applySilent(Expression2 funcLf, List<Object> args);

  public Optional<Object> applySilent(Expression2 lf, Object context, List<Object> args); 
}
