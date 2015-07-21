package com.jayantkrish.jklol.ccg.lexicon;

import java.io.Serializable;

import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;

/**
 * Scoring function for lexicon entries.
 * 
 * @author jayant
 *
 */
public interface LexiconScorer extends Serializable {

  double getCategoryWeight(int spanStart, int spanEnd, AnnotatedSentence sentence,
      CcgCategory category);
}
