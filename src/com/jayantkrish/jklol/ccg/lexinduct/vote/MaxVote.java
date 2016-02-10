package com.jayantkrish.jklol.ccg.lexinduct.vote;

import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.util.PairCountAccumulator;

public class MaxVote implements VotingStrategy {
  
  private final LexemeExtractor extractor;
  
  public MaxVote(LexemeExtractor extractor) {
    this.extractor = Preconditions.checkNotNull(extractor);
  }

  @Override
  public Set<LexiconEntry> vote(Set<LexiconEntry> currentLexicon, List<Set<LexiconEntry>> proposals) {
    Set<LexiconEntry> allProposals = Sets.newHashSet();
    List<Set<Lexeme>> exampleLexemes = Lists.newArrayList();
    Set<Lexeme> newLexemes = Sets.newHashSet();
    for (Set<LexiconEntry> exampleProposals : proposals) {
      // System.out.println("proposal: " + exampleProposals);
      allProposals.addAll(exampleProposals);

      Set<Lexeme> currentLexemes = extractor.extractLexemes(exampleProposals);
      exampleLexemes.add(currentLexemes);
      newLexemes.addAll(currentLexemes);
    }
    newLexemes.removeAll(extractor.extractLexemes(currentLexicon));

    /*
    System.out.println("original lexemes: " + extractor.extractLexemes(currentLexicon));
    System.out.println("new lexemes: " + newLexemes);
    */
    
    PairCountAccumulator<List<String>, Lexeme> votes = VoteAggregator.aggregateVotes(exampleLexemes);
    
    /*
    for (List<String> tokens : votes.keySet()) {
      System.out.println(tokens);
      for (Lexeme l : votes.getValues(tokens)) {
        System.out.println("  " + votes.getCount(tokens, l) + " " + l);
      }
    }
    */
    
    Set<Lexeme> maxVoted = Sets.newHashSet();
    for (List<String> tokens : Lexeme.lexemesToTokens(newLexemes)) {
      List<Lexeme> lexemesByProbability = votes.getOutcomesByProbability(tokens);
      for (int i = 0; i < lexemesByProbability.size(); i++) {
        if (newLexemes.contains(lexemesByProbability.get(i))) {
          maxVoted.add(lexemesByProbability.get(i));
          break;
        }
      }
    }
    
    Set<LexiconEntry> newLexicon = Sets.newHashSet(currentLexicon);
    for (LexiconEntry proposal : allProposals) {
      if (maxVoted.contains(extractor.extractLexeme(proposal))) {
        newLexicon.add(proposal);
      }
    }
    return newLexicon;
  }
}
