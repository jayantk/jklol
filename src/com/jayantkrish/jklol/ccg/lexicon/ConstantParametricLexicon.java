package com.jayantkrish.jklol.ccg.lexicon;

import java.util.Collections;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.models.parametric.ListSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

public class ConstantParametricLexicon implements ParametricCcgLexicon {
  private static final long serialVersionUID = 1L;
  
  private final CcgLexicon lexicon;
  
  public ConstantParametricLexicon(CcgLexicon lexicon) {
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
      SufficientStatistics currentParameters, List<String> wordSequence,
      List<String> posSequence, CcgCategory category, double count) {
    // Don't need to do anything.
  }
}
