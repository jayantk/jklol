package com.jayantkrish.jklol.ccg.lexicon;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.lexicon.SkipLexicon.SkipTrigger;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;

public class ParametricSkipLexicon implements ParametricCcgLexicon {
  private static final long serialVersionUID = 1L;

  private final ParametricCcgLexicon lexicon;

  public ParametricSkipLexicon(ParametricCcgLexicon lexicon) {
    this.lexicon = Preconditions.checkNotNull(lexicon);
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    return lexicon.getNewSufficientStatistics();
  }

  @Override
  public CcgLexicon getModelFromParameters(SufficientStatistics parameters) {
    return new SkipLexicon(lexicon.getModelFromParameters(parameters));
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters) {
    return lexicon.getParameterDescription(parameters);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters,
      int numFeatures) {
    return lexicon.getParameterDescription(parameters, numFeatures);
  }
  
  @Override
  public void incrementLexiconSufficientStatistics(SufficientStatistics gradient,
      SufficientStatistics currentParameters, int spanStart, int spanEnd,
      AnnotatedSentence sentence, Object trigger, CcgCategory category, double count) {
    SkipTrigger skipTrigger = (SkipTrigger) trigger;
    
    lexicon.incrementLexiconSufficientStatistics(gradient, currentParameters,
        skipTrigger.getTriggerSpanStart(), skipTrigger.getTriggerSpanEnd(),
        sentence, skipTrigger.getTrigger(), category, count);
  }
}
