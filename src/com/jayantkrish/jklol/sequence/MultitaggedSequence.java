package com.jayantkrish.jklol.sequence;

import java.util.List;

public interface MultitaggedSequence<I, O> {
  
  /**
   * Gets the length of this sequence, which is the number of items.
   * 
   * @return
   */
  int size();

  /**
   * Gets the items which are tagged. For example, in a part-of-speech
   * tagger, this returns the list of words to tag.
   * 
   * @return
   */
  List<I> getItems();
  
  List<O> getBestLabels();

  List<List<O>> getLabels();
  
  List<List<Double>> getLabelProbabilities();
}
