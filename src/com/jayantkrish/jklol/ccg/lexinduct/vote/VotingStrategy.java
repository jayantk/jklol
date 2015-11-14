package com.jayantkrish.jklol.ccg.lexinduct.vote;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.util.PairCountAccumulator;

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
  
  public static PairCountAccumulator<List<String>, Lexeme> aggregateVotes(List<Set<Lexeme>> exampleLexemes) {
    PairCountAccumulator<List<String>, Lexeme> votes = PairCountAccumulator.create();
    for (int i = 0; i < exampleLexemes.size(); i++) {
      Multimap<List<String>, Lexeme> stringLexemeMap = HashMultimap.create();
      for (Lexeme lexeme : exampleLexemes.get(i)) {
        stringLexemeMap.put(lexeme.getTokens(), lexeme);
      }

      for (List<String> tokens : stringLexemeMap.keySet()) {
        Collection<Lexeme> tokenLexemes = stringLexemeMap.get(tokens);
        for (Lexeme lexeme : tokenLexemes) {
          votes.incrementOutcome(tokens, lexeme, 1.0 / tokenLexemes.size());
        }
      }
    }
    return votes;
  }
}
