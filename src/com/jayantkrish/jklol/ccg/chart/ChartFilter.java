package com.jayantkrish.jklol.ccg.chart;

import com.jayantkrish.jklol.models.DiscreteVariable;

/**
 * Filter for discarding portions of the CCG beam. Chart entries for
 * which {@code apply} returns {@code false} are discarded.
 * 
 * @author jayantk
 */
public interface ChartFilter {

  /**
   * Returns {@code true} if {@code entry} is a valid chart entry
   * for the indicated span. If this method returns {@code false},
   * the given entry will be discarded (i.e., not included in the
   * search).
   * 
   * @param entry
   * @param spanStart
   * @param spanEnd
   * @param syntaxVarType
   * @return
   */
  public boolean apply(ChartEntry entry, int spanStart, int spanEnd,
      DiscreteVariable syntaxVarType);

  /**
   * Updates {@code chart} based on the set of possible terminals.
   * This method can be used to restrict the CCG search space based
   * on the words which actually appear in a sentence.
   * <p>
   * When this method is called, {@code chart} is initialized with
   * the terminal symbols (i.e., the words) and the possible lexicon
   * entries for each word.
   * 
   * @param chart
   */
  public void applyToTerminals(CcgChart chart);
}
