package com.jayantkrish.jklol.ccg.supertag;

import java.util.Collections;
import java.util.List;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.sequence.ListMultitaggedSequence;

public class ListSupertaggedSentence extends ListMultitaggedSequence<WordAndPos, HeadedSyntacticCategory> implements SupertaggedSentence {

  public ListSupertaggedSentence(List<WordAndPos> items, List<List<HeadedSyntacticCategory>> labels,
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
  public static ListSupertaggedSentence createWithUnobservedSupertags(List<String> words, List<String> pos) {
    return new ListSupertaggedSentence(WordAndPos.createExample(words, pos),
        Collections.nCopies(words.size(), Collections.<HeadedSyntacticCategory>emptyList()),
        Collections.nCopies(words.size(), Collections.<Double>emptyList()));
  }

  @Override
  public ListSupertaggedSentence replaceSupertags(List<List<HeadedSyntacticCategory>> supertags,
      List<List<Double>> labelProbabilities) {
    List<WordAndPos> words = getItems();
    return new ListSupertaggedSentence(words, supertags, labelProbabilities);
  }

  @Override
  public ListSupertaggedSentence removeSupertags() {
    List<WordAndPos> words = getItems();
    return replaceSupertags(Collections.nCopies(words.size(), Collections.<HeadedSyntacticCategory>emptyList()),
        Collections.nCopies(words.size(), Collections.<Double>emptyList()));
  }

  @Override
  public int size() {
    return getItems().size();
  }

  @Override
  public List<String> getWords() {
    List<String> words = Lists.newArrayList();
    for (WordAndPos wordAndPos : getItems()) {
      words.add(wordAndPos.getWord());
    }
    return words;
  }
  
  @Override
  public List<String> getPosTags() {
    List<String> posTags = Lists.newArrayList();
    for (WordAndPos wordAndPos : getItems()) {
      posTags.add(wordAndPos.getPos());
    }
    return posTags;
  }
  
  @Override
  public List<WordAndPos> getWordsAndPosTags() {
    return getItems();
  }

  @Override
  public List<List<HeadedSyntacticCategory>> getSupertags() {
    return getLabels();
  }
  
  @Override
  public List<List<Double>> getSupertagScores() {
    return getLabelProbabilities();
  }

  @Override
  public String toString() {
    return Joiner.on(" ").join(getItems());
  }
}
