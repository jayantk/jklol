package com.jayantkrish.jklol.sequence;

import com.google.common.base.Function;

public interface LocalContext<I> {

  /**
   * Gets the central item which this context surrounds.
   * 
   * @return
   */
  I getItem();

  /**
   * Gets an item to the left or right of the central item in this
   * context. Negative offsets get an item on the left (e.g., -2 gets
   * the second item on the left) and positive offsets get an item on
   * the right. If {@code relativeOffset} refers to a word off the end
   * of the sequence, then {@code endFunction} is invoked to produce the
   * return value.
   * 
   * @param relativeOffset
   * @return
   */
  I getItem(int relativeOffset, Function<? super Integer, I> endFunction);

  /**
   * Gets the largest offset for which {@code getItem} will remain within
   * the elements of the sequence.
   * 
   * @return
   */
  int getMaxOffset();
  
  /**
   * Gets the smallest offset for which {@code getItem} will remain within
   * the elements of the sequence.
   * @return
   */
  int getMinOffset();
}
