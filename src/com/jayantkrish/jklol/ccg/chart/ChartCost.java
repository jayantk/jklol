package com.jayantkrish.jklol.ccg.chart;

import com.jayantkrish.jklol.models.DiscreteVariable;

/**
 * Filter for discarding portions of the CCG beam. Chart entries for
 * which {@code apply} returns {@code false} are discarded.
 * 
 * @author jayantk
 */
public interface ChartCost {

  /**
   * Returns a cost for {@code entry} being added to the chart
   * at the indicated span. The cost is a log probability or linear cost.
   * If this method returns {@code Double.NEGATIVE_INFINITY},
   * the given entry will be discarded (i.e., not included in the
   * search).
   * 
   * @param entry
   * @param spanStart
   * @param spanEnd
   * @param syntaxVarType
   * @return
   */
  public double apply(ChartEntry entry, int spanStart, int spanEnd,
      DiscreteVariable syntaxVarType);
}
