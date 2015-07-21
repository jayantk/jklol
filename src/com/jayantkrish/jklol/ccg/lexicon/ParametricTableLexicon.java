package com.jayantkrish.jklol.ccg.lexicon;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A CCG lexicon with parameters for each word CCG
 * category mapping.
 * 
 * @author jayant
 *
 */
public class ParametricTableLexicon implements ParametricCcgLexicon {
  private static final long serialVersionUID = 2L;
  
  private final VariableNumMap terminalVar;
  private final VariableNumMap ccgCategoryVar;
  private final ParametricFactor terminalFamily;

  public ParametricTableLexicon(VariableNumMap terminalVar, VariableNumMap ccgCategoryVar,
      ParametricFactor terminalFamily) {
    this.terminalVar = Preconditions.checkNotNull(terminalVar);
    this.ccgCategoryVar = Preconditions.checkNotNull(ccgCategoryVar);
    this.terminalFamily = Preconditions.checkNotNull(terminalFamily);
  }

  public VariableNumMap getTerminalVar() {
    return terminalVar;
  }

  public VariableNumMap getCcgCategoryVar() {
    return ccgCategoryVar;
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    return terminalFamily.getNewSufficientStatistics();
  }

  @Override
  public TableLexicon getModelFromParameters(SufficientStatistics parameters) {
    DiscreteFactor terminalDistribution = terminalFamily.getModelFromParameters(parameters)
        .coerceToDiscrete();
    return new TableLexicon(terminalVar, ccgCategoryVar, terminalDistribution);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters) {
    return getParameterDescription(parameters, -1);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) {
    return terminalFamily.getParameterDescription(parameters, numFeatures);
  }

  @Override
  public void incrementLexiconSufficientStatistics(SufficientStatistics gradient,
      SufficientStatistics currentParameters, int spanStart, int spanEnd,
      AnnotatedSentence sentence, Object trigger, CcgCategory category, double count) {
    Assignment assignment = Assignment.unionAll(terminalVar.outcomeArrayToAssignment(trigger),
        ccgCategoryVar.outcomeArrayToAssignment(category));
    terminalFamily.incrementSufficientStatisticsFromAssignment(gradient,
        currentParameters, assignment, count);
  }
}
