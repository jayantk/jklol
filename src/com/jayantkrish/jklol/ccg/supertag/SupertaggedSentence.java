package com.jayantkrish.jklol.ccg.supertag;

import java.util.List;

import com.jayantkrish.jklol.ccg.SyntacticCategory;
import com.jayantkrish.jklol.sequence.ListTaggedSequence;

public class SupertaggedSentence extends ListTaggedSequence<WordAndPos, SyntacticCategory> {

  public SupertaggedSentence(List<WordAndPos> items, List<SyntacticCategory> labels) {
    super(items, labels);
  }
}
