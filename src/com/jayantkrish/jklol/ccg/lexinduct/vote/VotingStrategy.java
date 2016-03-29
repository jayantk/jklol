package com.jayantkrish.jklol.ccg.lexinduct.vote;

import java.util.List;
import java.util.Set;

import com.jayantkrish.jklol.ccg.LexiconEntry;

/**
 * A voting method for the {@link VotingLexiconInduction} lexicon induction
 * algorithm.
 * 
 * @author jayantk
 *
 */
public interface VotingStrategy {

  /**
   * Vote takes {@code currentLexicon} and a collection of lexicon entry
   * {@code proposals} for each training example and returns the
   * lexicon to be used on the next iteration. Note that the returned
   * lexicon is the complete lexicon, not just a delta of lexicon entries
   * to add to the current iteration's lexicon.   
   * 
   * @param currentLexicon
   * @param proposals
   * @return
   */
  public Set<LexiconEntry> vote(Set<LexiconEntry> currentLexicon, List<Set<LexiconEntry>> proposals);
}
