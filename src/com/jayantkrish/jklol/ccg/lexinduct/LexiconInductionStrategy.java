package com.jayantkrish.jklol.ccg.lexinduct;

import java.util.Set;

import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.LexiconEntry;

public interface LexiconInductionStrategy {

  /**
   * Proposes a set of changes to the lexicon of a CCG parser.
   * New entries are added to {@code entriesToAdd}, and removed
   * entries are added to {@code entriesToRemove}.
   * 
   * @param example
   * @param parser
   * @param entriesToAdd
   * @param entriesToRemove
   * @return
   */
  void proposeLexiconEntries(CcgExample example, CcgParser parser,
      Set<LexiconEntry> entriesToAdd, Set<LexiconEntry> entriesToRemove);
}
