package com.jayantkrish.jklol.ccg.lexicon;

import java.util.List;

import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.models.parametric.ParametricFamily;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

public interface ParametricCcgLexicon extends ParametricFamily<CcgLexicon> {

  void incrementLexiconSufficientStatistics(SufficientStatistics gradient,
      SufficientStatistics currentParameters, List<String> wordSequence,
      List<String> posSequence, CcgCategory category, double count);
}
