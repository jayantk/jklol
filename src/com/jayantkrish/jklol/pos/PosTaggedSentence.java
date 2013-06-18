package com.jayantkrish.jklol.pos;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.sequence.ListTaggedSequence;

/**
 * A part-of-speech (POS) tagged sentence.
 * 
 * @author jayantk
 */
public class PosTaggedSentence extends ListTaggedSequence<String, String> {

  public PosTaggedSentence(List<String> words, List<String> pos) {
    super(words, pos);
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
  
  public List<String> getWords() {
    return getItems();
  }

  public List<String> getPos() {
    return getLabels();
  }
}
