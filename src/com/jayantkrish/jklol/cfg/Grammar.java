package com.jayantkrish.jklol.cfg;

import java.util.List;
import java.util.Set;

/**
 * {@code Grammar} is the interface to {@link CfgParser} that determines which
 * rules can be instantiated in particular cells of a parse chart. In the most
 * simple case, all possible rules can be used in each cell (see
 * {@link BasicGrammar}). However, it may be desirable to restrict the set of
 * possible rules (to, say, highly probable rules) in order to reduce the
 * complexity of parsing. The getter methods of {@code Grammar} provide
 * additional arguments to allow this sort of heuristic pruning. If heuristic
 * pruning is used, the parses returned by {@code CfgParser} may be approximate.
 * 
 * @author jayantk
 */
public interface Grammar {

  /**
   * Gets the possible binary production rules which can be applied to construct
   * a nonterminal for ({@code spanStart}, {@code spanEnd}) from spans (
   * {@code spanStart}, {@code spanStart + splitIndex}) and (
   * {@code spanStart + splitIndex + 1}, {@code spanEnd}).
   * 
   * @param spanStart
   * @param spanEnd
   * @param splitIndex
   * @return
   */
  public Set<BinaryProduction> getBinaryProductionsForEntry(int spanStart,
      int spanEnd, int splitIndex);

  /**
   * Gets all terminal production rules which generate
   * {@code terminals.subList(spanStart, spanEnd + 1)}. {@code spanStart} and
   * {@code spanEnd} are inclusive indices.
   * 
   * @param terminals
   * @param spanStart
   * @param spanEnd
   * @return
   */
  public Set<TerminalProduction> getTerminalSpanProductions(List<Production> terminals,
      int spanStart, int spanEnd);

  /**
   * Gets all possible terminal production rules in the grammar.
   * 
   * @return
   */
  public Set<TerminalProduction> getAllTerminalProductions();
}
