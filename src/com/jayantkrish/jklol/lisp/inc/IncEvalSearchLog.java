package com.jayantkrish.jklol.lisp.inc;

/**
 * Logging function for storing the search space explored during
 * incremental evaluation.
 * 
 * @author jayantk
 *
 */
public interface IncEvalSearchLog {

  public void log(IncEvalState current, IncEvalState next, double cost);
}
