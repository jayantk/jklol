package com.jayantkrish.jklol.ccg.supertag;

import java.util.List;

import com.jayantkrish.jklol.ccg.SyntacticCategory;
import com.jayantkrish.jklol.sequence.ListMultitaggedSequence;

public class SupertaggedSentence extends ListMultitaggedSequence<WordAndPos, SyntacticCategory> {

  public SupertaggedSentence(List<WordAndPos> items, List<List<SyntacticCategory>> labels,
      List<List<Double>> labelProbabilities) {
    super(items, labels, labelProbabilities);
  }
}
