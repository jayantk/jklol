package com.jayantkrish.jklol.ccg.lexicon;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.ListSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.util.Assignment;

public class ParametricSyntaxLexiconScorer implements ParametricLexiconScorer {
  private static final long serialVersionUID = 1L;

  private final VariableNumMap terminalVar;
  private final VariableNumMap terminalPosVar;
  private final VariableNumMap terminalSyntaxVar;

  // Weights for pos tag / syntactic category pairs.
  private final ParametricFactor terminalPosFamily;

  // Weights for word / syntactic category pairs.
  // This factor is defined over terminalVar
  // and terminalSyntaxVar, and provides backoff weights
  // for different semantic realizations of the same word.
  private final ParametricFactor terminalSyntaxFamily;

  /**
   * Names of the parameter vectors governing each factor in the lexicon
   * entries.
   */
  public static final String TERMINAL_POS_PARAMETERS = "terminalPos";
  public static final String TERMINAL_SYNTAX_PARAMETERS = "terminalSyntax";

  public ParametricSyntaxLexiconScorer(VariableNumMap terminalVar, VariableNumMap terminalPosVar,
      VariableNumMap terminalSyntaxVar, ParametricFactor terminalPosFamily,
      ParametricFactor terminalSyntaxFamily) {
    this.terminalVar = Preconditions.checkNotNull(terminalVar);
    this.terminalPosVar = Preconditions.checkNotNull(terminalPosVar);
    this.terminalSyntaxVar = Preconditions.checkNotNull(terminalSyntaxVar);
    this.terminalPosFamily = Preconditions.checkNotNull(terminalPosFamily);
    Preconditions.checkArgument(terminalPosFamily.getVars().equals(terminalPosVar.union(terminalSyntaxVar)));
    this.terminalSyntaxFamily = Preconditions.checkNotNull(terminalSyntaxFamily);
    Preconditions.checkArgument(terminalSyntaxFamily.getVars().equals(terminalVar.union(terminalSyntaxVar)));
  }
  
  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    SufficientStatistics terminalPosParameters = terminalPosFamily.getNewSufficientStatistics();
    SufficientStatistics terminalSyntaxParameters = terminalSyntaxFamily.getNewSufficientStatistics();

    return new ListSufficientStatistics(
        Arrays.asList(TERMINAL_POS_PARAMETERS, TERMINAL_SYNTAX_PARAMETERS),
        Arrays.asList(terminalPosParameters, terminalSyntaxParameters));
  }

  @Override
  public LexiconScorer getModelFromParameters(SufficientStatistics parameters) {
    ListSufficientStatistics parameterList = parameters.coerceToList();
    DiscreteFactor terminalPosDistribution = terminalPosFamily.getModelFromParameters(parameterList
        .getStatisticByName(TERMINAL_POS_PARAMETERS)).coerceToDiscrete();
    DiscreteFactor terminalSyntaxDistribution = terminalSyntaxFamily.getModelFromParameters(parameterList
        .getStatisticByName(TERMINAL_SYNTAX_PARAMETERS)).coerceToDiscrete();

    return new SyntaxLexiconScorer(terminalVar, terminalPosVar, terminalSyntaxVar, 
        terminalPosDistribution, terminalSyntaxDistribution);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters) {
    return getParameterDescription(parameters, -1);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) {
    ListSufficientStatistics parameterList = parameters.coerceToList();
    StringBuilder sb = new StringBuilder();
    sb.append(terminalPosFamily.getParameterDescription(
        parameterList.getStatisticByName(TERMINAL_POS_PARAMETERS), numFeatures));
    sb.append(terminalSyntaxFamily.getParameterDescription(
        parameterList.getStatisticByName(TERMINAL_SYNTAX_PARAMETERS), numFeatures));
    return sb.toString();
  }

  @Override
  public void incrementLexiconSufficientStatistics(SufficientStatistics gradient,
      SufficientStatistics currentParameters, List<String> wordSequence,
      List<String> posSequence, CcgCategory category, double count) {
    incrementPosSufficientStatistics(gradient, currentParameters,
        posSequence.get(posSequence.size() - 1), category.getSyntax(), count);
    incrementLexiconSyntaxSufficientStatistics(gradient, currentParameters,
        wordSequence, category.getSyntax(), count);
  }

  private void incrementPosSufficientStatistics(SufficientStatistics gradient,
      SufficientStatistics currentParameters, String posTag,
      HeadedSyntacticCategory syntax, double count) {
    SufficientStatistics terminalPosGradient = gradient.coerceToList()
        .getStatisticByName(TERMINAL_POS_PARAMETERS);
    SufficientStatistics terminalPosParameters = currentParameters.coerceToList()
        .getStatisticByName(TERMINAL_POS_PARAMETERS);
    Assignment posAssignment = terminalPosVar.outcomeArrayToAssignment(posTag).union(
        terminalSyntaxVar.outcomeArrayToAssignment(syntax));
    if (terminalPosFamily.getVars().isValidAssignment(posAssignment)) {
      terminalPosFamily.incrementSufficientStatisticsFromAssignment(terminalPosGradient,
          terminalPosParameters, posAssignment, count);
    }
  }

  private void incrementLexiconSyntaxSufficientStatistics(SufficientStatistics gradient, 
      SufficientStatistics currentParameters, List<String> words,
      HeadedSyntacticCategory syntax, double count) {
    SufficientStatistics terminalSyntaxGradient = gradient.coerceToList()
        .getStatisticByName(TERMINAL_SYNTAX_PARAMETERS);
    SufficientStatistics terminalSyntaxParameters = currentParameters.coerceToList()
        .getStatisticByName(TERMINAL_SYNTAX_PARAMETERS);
    Assignment assignment = terminalVar.outcomeArrayToAssignment(words).union(
        terminalSyntaxVar.outcomeArrayToAssignment(syntax));
    if (terminalSyntaxFamily.getVars().isValidAssignment(assignment)) {
      terminalSyntaxFamily.incrementSufficientStatisticsFromAssignment(terminalSyntaxGradient,
          terminalSyntaxParameters, assignment, count);
    }
  }
}
