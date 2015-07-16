package com.jayantkrish.jklol.ccg.lexicon;

import java.io.Serializable;

import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.models.parametric.ParametricFamily;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;

public interface ParametricLexiconScorer extends Serializable, ParametricFamily<LexiconScorer> {

  void incrementLexiconSufficientStatistics(SufficientStatistics gradient,
      SufficientStatistics currentParameters, int spanStart, int spanEnd,
      AnnotatedSentence sentence, CcgCategory lexiconEntryCategory, double count);  
}
