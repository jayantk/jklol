package com.jayantkrish.jklol.ccg.lexicon;

import java.io.Serializable;
import java.util.List;

import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;

/**
 * The lexicon of a CCG parser which determines the initial 
 * mapping from terminal symbols to nonterminals in the
 * parse chart. 
 *  
 * @author jayant
 *
 */
public interface CcgLexicon extends Serializable {
  public static final String UNKNOWN_WORD_PREFIX = "UNK-";

  VariableNumMap getTerminalVar();
  
  /**
   * Gets the possible lexicon entries for {@code wordSequence} that
   * can be used in this parser. {@code alreadyGenerated} is a collection
   * of lexicon entries that have already been created for
   * {@code wordSequence}. 
   * 
   * @param wordSequence
   * @param posSequence
   * @param alreadyGenerated
   * @param spanStart
   * @param spanEnd
   * @param sentence
   * @return
   */
  List<LexiconEntry> getLexiconEntries(List<String> wordSequence,
      List<String> posSequence, List<LexiconEntry> alreadyGenerated,
      int spanStart, int spanEnd, AnnotatedSentence sentence);

  /**
   * Gets the unnormalized probability of generating {@code category}
   * for {@code wordSequence} and {@code posSequence}.
   * 
   * @param wordSequence
   * @param posSequence
   * @param category
   * @return
   */
  double getCategoryWeight(List<String> wordSequence, List<String> posSequence,
      CcgCategory category);
}