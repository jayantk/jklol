package com.jayantkrish.jklol.ccg.supertag;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

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
  
  public static List<WordAndPos> createExample(List<String> words, List<String> pos) {
    Preconditions.checkArgument(words.size() == pos.size());
    List<WordAndPos> examples = Lists.newArrayList();
    for (int i = 0; i < words.size(); i++) {
      examples.add(new WordAndPos(words.get(i), pos.get(i)));
    }
    return examples;
  }
}
