package com.jayantkrish.jklol.ccg.supertag;

import com.google.common.base.Preconditions;

/**
 * A word with a part-of-speech tag.
 * 
 * @author jayantk
 */
public class WordAndPos {
  
  private final String word;
  private final String pos;
  
  public WordAndPos(String word, String pos) {
    this.word = Preconditions.checkNotNull(word);
    this.pos = Preconditions.checkNotNull(pos);
  }

  public String getWord() {
    return word;
  }

  public String getPos() {
    return pos;
  }
}
