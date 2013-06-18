package com.jayantkrish.jklol.ccg.supertag;

import java.util.List;

import com.jayantkrish.jklol.ccg.SyntacticCategory;
import com.jayantkrish.jklol.sequence.SequenceTagger;

public interface Supertagger extends SequenceTagger<WordAndPos, SyntacticCategory> {

  @Override
  SupertaggedSentence tag(List<WordAndPos> input);
}
