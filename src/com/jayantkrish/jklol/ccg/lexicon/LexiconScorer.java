package com.jayantkrish.jklol.ccg.lexicon;

import java.io.Serializable;
import java.util.List;

/**
 * Scoring function for lexicon entries.
 * 
 * @author jayant
 *
 */
public interface LexiconScorer extends Serializable {

  InstantiatedLexiconScorer get(List<String> terminals,
      List<String> preprocessedTerminals, List<String> posTags);  
}
