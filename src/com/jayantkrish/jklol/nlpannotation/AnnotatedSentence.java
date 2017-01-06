package com.jayantkrish.jklol.nlpannotation;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.ccg.supertag.WordAndPos;

/**
 * A sentence with various annotations. This class represents
 * tokenized and part-of-speech tagged sentences, possibly with
 * additional annotations.
 * 
 * @author jayantk
 */
public class AnnotatedSentence {

  private final List<String> words;
  private final List<String> lowercaseWords;
  private final List<String> posTags;
  private final Map<String, Object> annotations;

  public AnnotatedSentence(List<String> words, List<String> posTags,
      Map<String, Object> annotations) {
    this.words = ImmutableList.copyOf(words);
    this.lowercaseWords = lowercaseInput(words);
    this.posTags = ImmutableList.copyOf(posTags);
    Preconditions.checkArgument(words.size() == posTags.size());
    this.annotations = Preconditions.checkNotNull(annotations);
  }

  public AnnotatedSentence(List<String> words, List<String> posTags) {
    this.words = ImmutableList.copyOf(words);
    this.lowercaseWords = lowercaseInput(words);
    this.posTags = ImmutableList.copyOf(posTags);
    Preconditions.checkArgument(words.size() == posTags.size());
    this.annotations = Collections.emptyMap();
  }

  /**
   * Gets the words in the sentence.
   * 
   * @return
   */
  public List<String> getWords() {
    return words;
  }

  public List<String> getWordsLowercase() {
    return lowercaseWords;
  }

  /**
   * Gets the part-of-speech tags of the words. Returns
   * a list of the same length as {@link getWords()}.
   * 
   * @return
   */
  public List<String> getPosTags() {
    return posTags;
  }
  
  public List<WordAndPos> getWordsAndPosTags() {
    return WordAndPos.createExample(words, posTags);
  }
  
  /**
   * Gets the number of words in the sentence.
   * 
   * @return
   */
  public int size() {
    return words.size();
  }

  public Object getAnnotation(String annotationName) {
    return annotations.get(annotationName);
  }
  
  public Map<String, Object> getAnnotations() {
    return annotations;
  }

  /**
   * Gets a copy of this annotated sentence containing the
   * provided annotation in addition to the annotations on
   * {@code this}.
   * 
   * This method overwrites any other annotation with the same
   * name.
   * 
   * @param annotationName
   * @param value
   * @return
   */
  public AnnotatedSentence addAnnotation(String annotationName, Object value) {
    Map<String, Object> newAnnotations = Maps.newHashMap(annotations);
    newAnnotations.put(annotationName, value);
    return new AnnotatedSentence(words, posTags, newAnnotations);
  }

  @Override
  public String toString() {
    return Joiner.on(" ").join(getWordsAndPosTags());
  }

  private static List<String> lowercaseInput(List<String> terminals) {
    List<String> preprocessedTerminals = Lists.newArrayList();
    for (String terminal : terminals) {
      preprocessedTerminals.add(terminal.toLowerCase());
    }
    return preprocessedTerminals;
  }
}
