package com.jayantkrish.jklol.lisp.inc;

import java.util.List;

import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.training.LogFunction;

/**
 * Interface for evaluating logical forms. 
 * 
 * @author jayantk
 *
 */
public interface IncEval {

  public void evaluateContinuation(IncEvalState state, IncEvalChart chart, LogFunction log);

  /**
   * Gets the environment in which logical forms are evaluated.
   * The returned environment may be mutated by the calling code.
   * 
   * @return
   */
  public Environment getEnvironment();

  /**
   * Produces a continuation from an expression. 
   * 
   * @param lf
   * @param env
   * @return
   */
  public Object lfToContinuation(Expression2 lf, Environment env);

  /**
   * Evaluates {@code lf} to completion using a beam search and 
   * {@code initialDiagram}.
   * 
   * @param lf
   * @param initialDiagram
   * @param beamSize
   * @return
   */
  public List<IncEvalState> evaluateBeam(Expression2 lf, Object initialDiagram,
      int beamSize);
  
  public List<IncEvalState> evaluateBeam(Expression2 lf, Object initialDiagram,
      Environment initialEnv, int beamSize);

  /**
   * Evaluates {@code lf} to completion using a beam search and 
   * {@code initialDiagram}. The search penalizes states by
   * log probability {@code cost} (pruned if {@code Double.NEGATIVE_INFINITY}).
   * 
   * @param lf
   * @param initialDiagram
   * @param cost
   * @param beamSize
   * @return
   */
  public List<IncEvalState> evaluateBeam(Expression2 lf, Object initialDiagram,
      IncEvalCost cost, int beamSize);
  
  public List<IncEvalState> evaluateBeam(Expression2 lf, Object initialDiagram,
      IncEvalCost cost, LogFunction log, int beamSize);

  public List<IncEvalState> evaluateBeam(Expression2 lf, Object initialDiagram,
      IncEvalCost cost, Environment initialEnv, LogFunction log, int beamSize);
  
  public List<IncEvalState> evaluateBeam(Expression2 lf, Object initialDiagram,
      IncEvalCost cost, Environment initialEnv, LogFunction log,
      IncEvalSearchLog searchLog, int beamSize);
}
