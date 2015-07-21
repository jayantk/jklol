package com.jayantkrish.jklol.ccg.lexicon;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.util.Assignment;

public class ParametricUnknownWordLexicon implements ParametricCcgLexicon {
  private static final long serialVersionUID = 1L;

  private final VariableNumMap terminalVar;
  private final VariableNumMap posVar;
  private final VariableNumMap ccgCategoryVar;
  private final ParametricFactor posCategoryFamily;
  
  public ParametricUnknownWordLexicon(VariableNumMap terminalVar, VariableNumMap posVar,
      VariableNumMap ccgCategoryVar, ParametricFactor posCategoryFamily) {
    this.terminalVar = Preconditions.checkNotNull(terminalVar);
    this.posVar = Preconditions.checkNotNull(posVar);
    this.ccgCategoryVar = Preconditions.checkNotNull(ccgCategoryVar);
    this.posCategoryFamily = Preconditions.checkNotNull(posCategoryFamily);
    
    Preconditions.checkArgument(posCategoryFamily.getVars().equals(posVar.union(ccgCategoryVar)));
  }
  
  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    return posCategoryFamily.getNewSufficientStatistics();
  }

  @Override
  public CcgLexicon getModelFromParameters(SufficientStatistics parameters) {
    DiscreteFactor f = posCategoryFamily.getModelFromParameters(parameters).coerceToDiscrete();
    return new UnknownWordLexicon(terminalVar, posVar, ccgCategoryVar, f);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters) {
    return posCategoryFamily.getParameterDescription(parameters);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) {
    return posCategoryFamily.getParameterDescription(parameters, numFeatures);
  }

  @Override
  public void incrementLexiconSufficientStatistics(SufficientStatistics gradient,
      SufficientStatistics currentParameters, int spanStart, int spanEnd,
      AnnotatedSentence sentence, Object trigger, CcgCategory category, double count) {
    Assignment a = posVar.outcomeArrayToAssignment(trigger).union(
        ccgCategoryVar.outcomeArrayToAssignment(category));
    posCategoryFamily.incrementSufficientStatisticsFromAssignment(gradient, currentParameters, a,
        count);
  }
}
