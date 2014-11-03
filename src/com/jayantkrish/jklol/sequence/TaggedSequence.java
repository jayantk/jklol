package com.jayantkrish.jklol.sequence;

import java.util.List;

/**
 * A sequence of items tagged with labels.
 * 
 * @author jayantk
 * @param <I>
 * @param <O>
 */
public interface TaggedSequence<I, O> {

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

  /**
   * Gets the labels (tags) for each item. May be {@code null}, in
   * which case the labels are not known.
   * 
   * @return
   */
  List<O> getLabels();

  /**
   * Gets the local context surrounding each element of the sequence.
   * 
   * @return
   */
  List<LocalContext<I>> getLocalContexts();
}
