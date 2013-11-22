package com.jayantkrish.jklol.ccg.lexicon;

import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.models.parametric.ParametricFamily;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

public interface ParametricCcgLexicon extends ParametricFamily<CcgLexicon> {

  void incrementLexiconSufficientStatistics(SufficientStatistics gradient, CcgParse parse, double count);
}
