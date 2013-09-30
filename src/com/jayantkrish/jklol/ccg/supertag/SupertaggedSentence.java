package com.jayantkrish.jklol.ccg.supertag;

import java.util.List;

import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.sequence.ListMultitaggedSequence;

public class SupertaggedSentence extends ListMultitaggedSequence<WordAndPos, HeadedSyntacticCategory> {

  public SupertaggedSentence(List<WordAndPos> items, List<List<HeadedSyntacticCategory>> labels,
      List<List<Double>> labelProbabilities) {
    super(items, labels, labelProbabilities);
  }
}
