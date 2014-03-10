package com.jayantkrish.jklol.ccg.lexinduct;

import java.util.Set;

import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.LexiconEntry;

public interface LexiconInductionStrategy {

  /**
   * Proposes a set of lexicon entries to add to the CCG
   * lexicon.
   * 
   * @param example
   * @param parser
   * @return
   */
  Set<LexiconEntry> proposeLexiconEntries(CcgExample example, CcgParser parser);
}
