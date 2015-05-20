package com.jayantkrish.jklol.ccg.lexicon;

import java.util.List;

import com.jayantkrish.jklol.ccg.CcgCategory;

public interface InstantiatedLexiconScorer {

  double getCategoryWeight(int spanStart, int spanEnd, List<String> terminalValue,
      List<String> posTags, CcgCategory category);
  
}
