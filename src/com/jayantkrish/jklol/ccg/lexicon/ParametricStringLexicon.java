package com.jayantkrish.jklol.ccg.lexicon;

import java.util.Collections;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.models.parametric.ListSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

public class ParametricStringLexicon implements ParametricCcgLexicon {
  private static final long serialVersionUID = 1L;
  
  private final StringLexicon lexicon;
  
  public ParametricStringLexicon(StringLexicon lexicon) {
    this.lexicon = Preconditions.checkNotNull(lexicon);
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    return new ListSufficientStatistics(Collections.<String>emptyList(),
        Collections.<SufficientStatistics>emptyList());
  }

  @Override
  public CcgLexicon getModelFromParameters(SufficientStatistics parameters) {
    return lexicon;
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters) {
    return getParameterDescription(parameters, -1);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) {
    return "";
  }

  @Override
  public void incrementLexiconSufficientStatistics(SufficientStatistics gradient,
      SufficientStatistics currentParameters, CcgParse parse, double count) {
    // Don't need to do anything.
  }
}
