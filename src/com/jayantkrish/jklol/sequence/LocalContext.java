package com.jayantkrish.jklol.sequence;

import java.util.List;

import com.google.common.base.Preconditions;

public class LocalContext<I> {
  private final List<I> items;
  private final int wordIndex;

  public LocalContext(List<I> items, int wordIndex) {
    this.items = Preconditions.checkNotNull(items);
    this.wordIndex = wordIndex;

    Preconditions.checkArgument(wordIndex >= 0 && wordIndex < items.size());
  }

  /**
   * Gets the central item which this context surrounds.
   * 
   * @return
   */
  public I getItem() {
    return items.get(wordIndex);
  }

  /**
   * Gets an item to the left or right of the central item in this
   * context. Negative offsets get an item on the left (e.g., -2 gets
   * the second item on the left) and positive offsets get an item on
   * the right. 
   * 
   * TODO: items off of the end of the sentence.
   * 
   * @param relativeOffset
   * @return
   */
  public I getItem(int relativeOffset) {
    int index = wordIndex + relativeOffset;

    if (index < 0) {
      return ("<START_" + index + ">").intern();
    } else if (index >= sentence.size()) {
      int endWordIndex = index - (sentence.size() - 1);
      return ("<END_" + endWordIndex + ">").intern();
    } else {
      return items.get(index);
    }
  }
}
