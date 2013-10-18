package com.jayantkrish.jklol.ccg.lexicon;

import java.util.Arrays;
import java.util.List;

import com.jayantkrish.jklol.ccg.LexiconEntry;

/**
 * Implementations of common {@code CcgLexicon} methods.
 * 
 * @author jayant
 *
 */
public abstract class AbstractCcgLexicon implements CcgLexicon {
  
  @Override
  public List<LexiconEntry> getLexiconEntriesWithUnknown(String word, String posTag) {
    return getLexiconEntriesWithUnknown(Arrays.asList(word), Arrays.asList(posTag));
  }
}
