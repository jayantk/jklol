package com.jayantkrish.jklol.ccg.supertag;

import java.io.Serializable;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/**
 * A word with a part-of-speech tag.
 * 
 * @author jayantk
 */
public class WordAndPos implements Serializable {
  private static final long serialVersionUID = 1L;

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
  
  @Override
  public String toString() {
    return word + "/" + pos;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((pos == null) ? 0 : pos.hashCode());
    result = prime * result + ((word == null) ? 0 : word.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    WordAndPos other = (WordAndPos) obj;
    if (pos == null) {
      if (other.pos != null)
        return false;
    } else if (!pos.equals(other.pos))
      return false;
    if (word == null) {
      if (other.word != null)
        return false;
    } else if (!word.equals(other.word))
      return false;
    return true;
  }
}
