package com.jayantkrish.jklol.ccg.lexinduct.vote;

import java.util.Collection;
import java.util.Set;

import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.LexiconEntry;

public abstract class LexemeExtractor {

  public abstract Lexeme extractLexeme(LexiconEntry entry);
  
  public Set<Lexeme> extractLexemes(Collection<LexiconEntry> entries) {
    Set<Lexeme> lexemes = Sets.newHashSet();
    for (LexiconEntry entry : entries) {
      lexemes.add(extractLexeme(entry));
    }
    return lexemes;
  }
}
