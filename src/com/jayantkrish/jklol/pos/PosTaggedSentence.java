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

  public List<String> getWords() {
    return words;
  }
  
  public List<LocalContext> getLocalContexts() {
    List<LocalContext> contexts = Lists.newArrayList();
    for (int i = 0; i < words.size(); i++) {
      contexts.add(new LocalContext(words.subList(i, i + 1), pos.get(i)));
    }
    return contexts;
  }
  
  public List<String> getPos() {
    return pos;
  }
  
  public static class LocalContext {
    private final List<String> words;
    private final String pos;
    
    public LocalContext(List<String> words, String pos) {
      // TODO: word indexes relative to original word.
      this.words = ImmutableList.copyOf(words);
      this.pos = Preconditions.checkNotNull(pos);
    }
    
    /**
     * Gets the central word which this context surrounds.
     * 
     * @return
     */
    public String getWord() {
      return words.get(0);
    }

    public List<String> getWords() {
      return words;
    }
    
    public String getPos() {
      return pos;
    }
  }
}
