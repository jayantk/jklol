package com.jayantkrish.jklol.ccg.lexinduct.vote;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.jayantkrish.jklol.util.PairCountAccumulator;

public class VoteAggregator {
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
