package com.jayantkrish.jklol.ccg.supertag;

import java.util.List;

import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;

public interface SupertaggedSentence {

  /**
   * Returns a copy of {@code this} with the supertags and their probabilities
   * replaced by the given values.
   *  
   * @param supertags
   * @param labelProbabilities
   * @return
   */
  SupertaggedSentence replaceSupertags(List<List<HeadedSyntacticCategory>> supertags,
      List<List<Double>> labelProbabilities);

  /**
   * Returns a copy of {@code this} with all supertags removed.
   * 
   * @return
   */
  SupertaggedSentence removeSupertags();

  int size();

  List<String> getWords();
  
  List<String> getPosTags();
  
  List<WordAndPos> getWordsAndPosTags();

  List<List<HeadedSyntacticCategory>> getSupertags();
  
  /**
   * Scores may or may not be probabilities, but higher scores are
   * always better.
   * 
   * @return
   */
  List<List<Double>> getSupertagScores();
}
