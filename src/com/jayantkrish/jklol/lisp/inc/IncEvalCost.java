package com.jayantkrish.jklol.lisp.inc;


public interface IncEvalCost {

 /**
  * Returns a cost for {@code state} being added to the search.
  * The cost is a log probability or linear cost.
  * If this method returns {@code Double.NEGATIVE_INFINITY},
  * the given entry will be discarded (i.e., not included in the
  * search).
  */
  public double apply(IncEvalState state);

}
