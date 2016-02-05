package com.jayantkrish.jklol.ccg.gi;

import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.lisp.Environment;

/**
 * Oracle for evaluating logical forms during CCG parsing. 
 * 
 * @author jayantk
 *
 */
public interface IncrementalEval {

  /**
   * Evaluates the continuation in {@code state}, producing zero or
   * more future continuations that are stored in {@code resultQueue}.
   * 
   * @param state
   * @param resultQueue
   */
  public void evaluateContinuation(IncrementalEvalState state, List<IncrementalEvalState> resultQueue);
  
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
   * Produces a continuation from an expression. This method 
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
  public List<IncrementalEvalState> evaluateBeam(Expression2 lf, Object initialDiagram, int beamSize);
  
  public static class IncrementalEvalState {
    private final Object continuation;
    private final Environment environment;

    private final Object denotation;
    private final Object diagram;
    private final double prob;

    public IncrementalEvalState(Object continuation, Environment environment,
        Object denotation, Object diagram, double prob) {
      this.continuation = continuation;
      this.environment = environment;
      this.denotation = denotation;
      this.diagram = diagram;
      this.prob = prob;

      // Both continuation and continuationEnv must be null or not-null.
      Preconditions.checkArgument(!(continuation == null ^ environment == null));
    }

    public final Object getContinuation() {
      return continuation;
    }
    
    public final Environment getEnvironment() {
      return environment;
    }

    public final Object getDenotation() {
      return denotation;
    }

    public final Object getDiagram() {
      return diagram;
    }

    public final double getProb() {
      return prob;
    }
    
    @Override
    public String toString() {
      return continuation + " " + denotation + " " + diagram + " " + prob;
    }
  }
}
