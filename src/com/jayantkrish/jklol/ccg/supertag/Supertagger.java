package com.jayantkrish.jklol.ccg.supertag;

import java.util.List;

import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.sequence.SequenceTagger;

public interface Supertagger extends SequenceTagger<WordAndPos, HeadedSyntacticCategory> {

  @Override
  SupertaggedSentence multitag(List<WordAndPos> input, double threshold);
}
