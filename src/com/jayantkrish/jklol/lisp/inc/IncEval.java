package com.jayantkrish.jklol.lisp.inc;

import java.util.List;

import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.gi.GroundedCcgParse;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.training.LogFunction;

/**
 * Oracle for evaluating logical forms during CCG parsing. 
 * 
 * @author jayantk
 *
 */
public interface IncEval {

  /**
   * Evaluates the continuation in {@code state}, producing zero or
   * more future continuations that are stored in {@code resultQueue}.
   * 
   * @param state
   * @param resultQueue
   */
  public void evaluateContinuation(IncEvalState state, List<IncEvalState> resultQueue);
  
  /**
   * Evaluates the continuation in {@code state}, producing zero or
   * more future continuations that are stored in {@code resultQueue}.
   * 
   * @param state
   * @param resultQueue
   * @param log
   */
  public void evaluateContinuation(IncEvalState state, List<IncEvalState> resultQueue,
      LogFunction log);
  
  /**
   * Gets the environment in which logical forms are evaluated.
   * The returned environment may be mutated by the calling code.
   * 
   * @return
   */
  public Environment getEnvironment();
  
  /**
   * Produces a continuation from a parse. The continuation represents
   * an evaluatable object (in the simplest case, the evaluatable expression
   * itself) that can be used in {@code evaluateContinuation}. The returned
   * continuation should use any previous evaluation results from {@code parse}
   * instead of re-evaluating these expressions.
   * 
   * @param parse
   * @param env
   * @return
   */
  public Object parseToContinuation(GroundedCcgParse parse, Environment env);
  
  /**
   * Produces a continuation from an expression. 
   * 
   * @param lf
   * @param env
   * @return
   */
  public Object lfToContinuation(Expression2 lf, Environment env);
  
  /**
   * Returns {@code true} if a CCG parse with root {@code syntax}
   * produces a logical form that should be evaluated.
   * 
   * @param syntax
   * @return
   */
  public boolean isEvaluatable(HeadedSyntacticCategory syntax);

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
      IncEvalCost cost, Environment initialEnv, LogFunction log,
      int beamSize);
}
