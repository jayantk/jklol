package com.jayantkrish.jklol.pos;

import java.util.List;

import com.jayantkrish.jklol.sequence.SequenceTagger;

public interface PosTagger extends SequenceTagger<String, String> {
  
  @Override
  PosTaggedSentence tag(List<String> words);
}
