package com.jayantkrish.jklol.ccg.lexinduct.vote;

import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.util.PairCountAccumulator;

public class ConsensusVote implements VotingStrategy {

  private final LexemeExtractor extractor;
  private final VotingStrategy voter;
  
  public ConsensusVote(LexemeExtractor extractor) {
    this.extractor = Preconditions.checkNotNull(extractor);
    // The correct implementation of consensus vote uses max vote 
    this.voter = new MaxVote(extractor);
  }

  @Override
  public Set<LexiconEntry> vote(Set<LexiconEntry> currentLexicon, List<Set<LexiconEntry>> proposals) {
    List<Set<Lexeme>> exampleLexemes = Lists.newArrayList();
    for (int i = 0; i < proposals.size(); i++) {
      exampleLexemes.add(extractor.extractLexemes(proposals.get(i)));
    }

    Set<Lexeme> removedLexemes = Sets.newHashSet();
    boolean keepGoing = true;
    while (keepGoing) {
      // Filter lexemes for each example.
      for (int i = 0; i < proposals.size(); i++) {
        for (List<String> tokens : Lexeme.lexemesToTokens(exampleLexemes.get(i))) {

          Set<Lexeme> filteredLexemes = Sets.newHashSet();
          for (Lexeme lexeme : exampleLexemes.get(i)) {
            if (!removedLexemes.contains(lexeme) || !lexeme.getTokens().equals(tokens)) {
              filteredLexemes.add(lexeme);
            }
          }

          if (Lexeme.lexemesToTokens(filteredLexemes).contains(tokens)) {
            exampleLexemes.set(i, filteredLexemes);
          }
        }
      }

      PairCountAccumulator<List<String>, Lexeme> votes = VotingStrategy.aggregateVotes(exampleLexemes);

      Set<Lexeme> allLexemes = Sets.newHashSet();
      for (Set<Lexeme> lexemes : exampleLexemes) {
        allLexemes.addAll(lexemes);
      }

      keepGoing = false;
      for (List<String> tokens : Lexeme.lexemesToTokens(allLexemes)) {
        List<Lexeme> lexemesByProbability = votes.getOutcomesByProbability(tokens);
        Lexeme leastVoted = lexemesByProbability.get(lexemesByProbability.size() - 1);
        double minProb = votes.getCount(tokens, leastVoted);

        for (int i = lexemesByProbability.size() - 1; i >= 0; i--) {
          Lexeme current = lexemesByProbability.get(i);
          if (votes.getCount(tokens, leastVoted) == minProb) {
            if (!removedLexemes.contains(current)) {
              // Keep looping and voting while the set of removed lexemes
              // has changed.
              keepGoing = true;
              removedLexemes.add(current);
            }
          } else {
            // Guaranteed that no other lexemes have the minimum vote count,
            // because they're sorted by probability.
            break;
          }
        }
      }
    }

    List<Set<LexiconEntry>> filteredProposals = Lists.newArrayList();
    for (int i = 0; i < proposals.size(); i++) {
      Set<LexiconEntry> filtered = Sets.newHashSet();
      for (LexiconEntry proposal : proposals.get(i)) {
        if (exampleLexemes.get(i).contains(extractor.extractLexeme(proposal))) {
          filtered.add(proposal);
        }
      }
    }
    
    System.out.println(filteredProposals);

    return voter.vote(currentLexicon, filteredProposals);
  }
}
