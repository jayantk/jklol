package com.jayantkrish.jklol.pos;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * A part-of-speech (POS) tagged sentence.
 * 
 * @author jayantk
 */
public class PosTaggedSentence {
  private final List<String> words;
  private final List<String> pos;

  public PosTaggedSentence(List<String> words, List<String> pos) {
    Preconditions.checkArgument(words.size() == pos.size());

    this.words = ImmutableList.copyOf(words);
    this.pos = ImmutableList.copyOf(pos);
  }

  public static PosTaggedSentence parseFrom(String line) {
    String[] chunks = line.split(" ");
    Preconditions.checkState(chunks.length % 2 == 0, "Invalid input line: " + line);

    List<String> words = Lists.newArrayList();
    List<String> pos = Lists.newArrayList();
    for (int i = 0; i < chunks.length; i += 2) {
      words.add(chunks[i].intern());
      pos.add(chunks[i + 1].intern());
    }

    return new PosTaggedSentence(words, pos);
  }
  
  /**
   * Gets the number of words / pos tags in this sentence.
   * 
   * @return
   */
  public int size() {
    return words.size();
  }

  public List<String> getWords() {
    return words;
  }

  public List<LocalContext> getLocalContexts() {
    List<LocalContext> contexts = Lists.newArrayList();
    for (int i = 0; i < words.size(); i++) {
      contexts.add(new LocalContext(this, i));
    }
    return contexts;
  }

  public List<String> getPos() {
    return pos;
  }

  public static class LocalContext {
    private final PosTaggedSentence sentence;
    private final int wordIndex;

    public LocalContext(PosTaggedSentence sentence, int wordIndex) {
      this.sentence = Preconditions.checkNotNull(sentence);
      this.wordIndex = wordIndex;
      
      Preconditions.checkArgument(wordIndex >= 0 && wordIndex < sentence.size());
    }

    /**
     * Gets the central word which this context surrounds.
     * 
     * @return
     */
    public String getWord() {
      return sentence.getWords().get(wordIndex);
    }

    /**
     * Gets a word to the left or right of the central word in this
     * context. Negative offsets get a word on the left (i.e., -2 gets
     * the second word on the left) and positive offsets get a word on
     * the right. Words off to the left or right of the sentence return
     * the special words <START_(offset)> and <END_(offset)>.
     * 
     * @param relativeOffset
     * @return
     */
    public String getWord(int relativeOffset) {
      int index = wordIndex + relativeOffset;
      
      if (index < 0) {
        return ("<START_" + index + ">").intern();
      } else if (index >= sentence.size()) {
        int endWordIndex = index - (sentence.size() - 1);
        return ("<END_" + endWordIndex + ">").intern();
      } else {
        return sentence.getWords().get(index);
      }
    }
  }
}
