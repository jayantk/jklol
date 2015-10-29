package com.jayantkrish.jklol.ccg.lexinduct.vote;

import java.util.Set;

import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis;

public class PredicateLexemeExtractor extends LexemeExtractor {
  
  private final Set<String> predicatesToRetain;
  
  public PredicateLexemeExtractor(Set<String> predicatesToRetain) {
    this.predicatesToRetain = Sets.newHashSet(predicatesToRetain);
  }

  @Override
  public Lexeme extractLexeme(LexiconEntry entry) {
    Expression2 lf = entry.getCategory().getLogicalForm();
    Set<String> freeVars = StaticAnalysis.getFreeVariables(lf);
    freeVars.removeAll(predicatesToRetain);
    return new Lexeme(entry.getWords(), freeVars);
  }
}
