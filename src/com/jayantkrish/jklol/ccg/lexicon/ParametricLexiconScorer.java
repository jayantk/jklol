package com.jayantkrish.jklol.ccg.lexicon;

import java.io.Serializable;
import java.util.List;

import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.models.parametric.ParametricFamily;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

public interface ParametricLexiconScorer extends Serializable, ParametricFamily<LexiconScorer> {

  void incrementLexiconSufficientStatistics(SufficientStatistics gradient,
      SufficientStatistics currentParameters, int spanStart, int spanEnd, List<String> sentenceWords,
      List<String> sentencePreprocessedWords, List<String> sentencePos, List<String> lexiconEntryWords,
      List<String> lexiconEntryPos, CcgCategory lexiconEntryCategory, double count);  
}
