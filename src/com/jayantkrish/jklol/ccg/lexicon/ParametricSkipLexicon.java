package com.jayantkrish.jklol.ccg.lexicon;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.lexicon.SkipLexicon.SkipTrigger;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.parametric.ListSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.util.Assignment;

public class ParametricSkipLexicon implements ParametricCcgLexicon {
  private static final long serialVersionUID = 1L;

  private final ParametricCcgLexicon lexicon;
  private final ParametricFactor skipFamily;

  public ParametricSkipLexicon(ParametricCcgLexicon lexicon, ParametricFactor skipFamily) {
    this.lexicon = Preconditions.checkNotNull(lexicon);
    this.skipFamily = Preconditions.checkNotNull(skipFamily);
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    SufficientStatistics lex = lexicon.getNewSufficientStatistics();
    SufficientStatistics skip = skipFamily.getNewSufficientStatistics();

    return new ListSufficientStatistics(Arrays.asList("lex", "skip"), Arrays.asList(lex, skip));
  }

  @Override
  public CcgLexicon getModelFromParameters(SufficientStatistics parameters) {
    List<SufficientStatistics> parameterList = parameters.coerceToList().getStatistics();
    CcgLexicon instantiatedLex = lexicon.getModelFromParameters(parameterList.get(0));
    DiscreteFactor instantiatedFactor = skipFamily.getModelFromParameters(parameterList.get(1))
        .coerceToDiscrete();;
    
    return new SkipLexicon(instantiatedLex, instantiatedFactor);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters) {
    return getParameterDescription(parameters, -1);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters,
      int numFeatures) {
    List<SufficientStatistics> parameterList = parameters.coerceToList().getStatistics();
    StringBuilder sb = new StringBuilder();
    sb.append(lexicon.getParameterDescription(parameterList.get(0), numFeatures));
    sb.append(skipFamily.getParameterDescription(parameterList.get(1), numFeatures));
    return sb.toString();
  }
  
  @Override
  public void incrementLexiconSufficientStatistics(SufficientStatistics gradient,
      SufficientStatistics currentParameters, int spanStart, int spanEnd,
      AnnotatedSentence sentence, Object trigger, CcgCategory category, double count) {
    List<SufficientStatistics> gradientList = gradient.coerceToList().getStatistics();
    List<SufficientStatistics> parameterList = currentParameters.coerceToList().getStatistics();
    
    SkipTrigger skipTrigger = (SkipTrigger) trigger;
    lexicon.incrementLexiconSufficientStatistics(gradientList.get(0), parameterList.get(0),
        skipTrigger.getTriggerSpanStart(), skipTrigger.getTriggerSpanEnd(),
        sentence, skipTrigger.getTrigger(), category, count);

    List<String> lcWords = sentence.getWordsLowercase();
    for (int i = spanStart; i < skipTrigger.getTriggerSpanStart(); i++) {
      String word = lcWords.get(i);
      Assignment a = skipFamily.getVars().outcomeArrayToAssignment(Arrays.asList(word));
      if (skipFamily.getVars().isValidAssignment(a)) {
        skipFamily.incrementSufficientStatisticsFromAssignment(gradientList.get(1),
            parameterList.get(1), a, count);
      }
    }

    for (int i = skipTrigger.getTriggerSpanEnd() + 1; i <= spanEnd; i++) {
      String word = lcWords.get(i);
      Assignment a = skipFamily.getVars().outcomeArrayToAssignment(Arrays.asList(word));
      if (skipFamily.getVars().isValidAssignment(a)) {
        skipFamily.incrementSufficientStatisticsFromAssignment(gradientList.get(1),
            parameterList.get(1), a, count);
      }
    }
  }
}
