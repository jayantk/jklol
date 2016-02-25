package com.jayantkrish.jklol.lisp.inc;

import com.jayantkrish.jklol.lisp.inc.IncEval.IncEvalState;

public interface IncEvalCost {

 /**
  * Returns a cost for {@code state} being added to the search.
  * The cost is a log probability or linear cost.
  * If this method returns {@code Double.NEGATIVE_INFINITY},
  * the given entry will be discarded (i.e., not included in the
  * search).
  */
  public abstract double apply(IncEvalState state);

}
