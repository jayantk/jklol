package com.jayantkrish.jklol.ccg.supertag;

import java.util.Collections;
import java.util.List;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.sequence.ListMultitaggedSequence;

public class SupertaggedSentence extends ListMultitaggedSequence<WordAndPos, HeadedSyntacticCategory> {

  public SupertaggedSentence(List<WordAndPos> items, List<List<HeadedSyntacticCategory>> labels,
      List<List<Double>> labelProbabilities) {
    super(items, labels, labelProbabilities);
  }
  
  /**
   * Creates a supertagged sentence where the supertags for each word
   * are unobserved. Using this sentence during CCG parsing allows any
   * syntactic category to be assigned to each word. 
   * 
   * @param words
   * @param pos
   * @return
   */
  public static SupertaggedSentence createWithUnobservedSupertags(List<String> words, List<String> pos) {
    return new SupertaggedSentence(WordAndPos.createExample(words, pos),
        Collections.nCopies(words.size(), Collections.<HeadedSyntacticCategory>emptyList()),
        Collections.nCopies(words.size(), Collections.<Double>emptyList()));
  }
  
  /**
   * Returns a copy of {@code this} with the supertags and their probabilities
   * replaced by the given values.
   *  
   * @param supertags
   * @param labelProbabilities
   * @return
   */
  public SupertaggedSentence replaceSupertags(List<List<HeadedSyntacticCategory>> supertags,
      List<List<Double>> labelProbabilities) {
    List<WordAndPos> words = getItems();
    return new SupertaggedSentence(words, supertags, labelProbabilities);
  }

  /**
   * Returns a copy of {@code this} with all supertags removed.
   * 
   * @return
   */
  public SupertaggedSentence removeSupertags() {
    List<WordAndPos> words = getItems();
    return replaceSupertags(Collections.nCopies(words.size(), Collections.<HeadedSyntacticCategory>emptyList()),
        Collections.nCopies(words.size(), Collections.<Double>emptyList()));
  }

  public List<String> getWords() {
    List<String> words = Lists.newArrayList();
    for (WordAndPos wordAndPos : getItems()) {
      words.add(wordAndPos.getWord());
    }
    return words;
  }
  
  public List<String> getPosTags() {
    List<String> posTags = Lists.newArrayList();
    for (WordAndPos wordAndPos : getItems()) {
      posTags.add(wordAndPos.getPos());
    }
    return posTags;
  }
  
  public List<List<HeadedSyntacticCategory>> getSupertags() {
    return getLabels();
  }
  
  @Override
  public String toString() {
    return Joiner.on(" ").join(getItems());
  }
}
