package com.jayantkrish.jklol.ccg.lexicon;

import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.util.Assignment;

public class ParametricFactorLexiconScorer implements ParametricLexiconScorer {
  private static final long serialVersionUID = 1L;

  private final VariableNumMap wordVar;
  private final VariableNumMap categoryVar;
  private final VariableNumMap vars;
  private final ParametricFactor family;
  
  /**
   * Names of the parameter vectors governing each factor in the lexicon
   * entries.
   */
  public static final String TERMINAL_PARAMETERS = "terminals";
  public static final String TERMINAL_FEATURE_PARAMETERS = "terminalFeatures";
  public static final String TERMINAL_FEATURE_VAR_NAME = "terminalFeaturesVar";

  public ParametricFactorLexiconScorer(VariableNumMap wordVar,
      VariableNumMap categoryVar, ParametricFactor family) {
    this.wordVar = Preconditions.checkNotNull(wordVar);
    this.categoryVar = Preconditions.checkNotNull(categoryVar);
    this.family = Preconditions.checkNotNull(family);
    Preconditions.checkArgument(wordVar.getOnlyVariableNum() < categoryVar.getOnlyVariableNum());
    this.vars = wordVar.union(categoryVar);
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    return family.getNewSufficientStatistics();
  }

  @Override
  public FactorLexiconScorer getModelFromParameters(SufficientStatistics parameters) {
    DiscreteFactor factor = family.getModelFromParameters(parameters).coerceToDiscrete();
    return new FactorLexiconScorer(wordVar, categoryVar, factor);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters) {
    return getParameterDescription(parameters, -1);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) {
    return family.getParameterDescription(parameters, numFeatures);
  }

  @Override
  public void incrementLexiconSufficientStatistics(SufficientStatistics gradient,
      SufficientStatistics currentParameters, int spanStart, int spanEnd,
      AnnotatedSentence sentence, CcgCategory category, double count) {
    
    List<String> words = sentence.getWords().subList(spanStart, spanEnd + 1);
    Assignment a = vars.outcomeArrayToAssignment(words, category);
    if (vars.isValidAssignment(a)) {
      family.incrementSufficientStatisticsFromAssignment(gradient, currentParameters, a, count);
    }
  }
}
