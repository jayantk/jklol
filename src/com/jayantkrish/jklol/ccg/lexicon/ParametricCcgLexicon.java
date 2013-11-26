package com.jayantkrish.jklol.ccg.lexicon;

import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.supertag.SupertaggedSentence;
import com.jayantkrish.jklol.models.parametric.ParametricFamily;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

public interface ParametricCcgLexicon<T extends SupertaggedSentence> extends
ParametricFamily<CcgLexicon<T>> {

  @Override
  ParametricCcgLexicon<T> rescaleFeatures(SufficientStatistics rescaling);

  void incrementLexiconSufficientStatistics(SufficientStatistics gradient, CcgParse parse, double count);
}
