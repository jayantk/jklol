package com.jayantkrish.jklol.ccg.lexicon;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.ListSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A CCG lexicon parameterized using words, parts-of-speech,
 * and syntactic category.
 * 
 * @author jayant
 *
 */
public class ParametricTableLexicon implements ParametricCcgLexicon {
  private static final long serialVersionUID = 1L;
  
  private final VariableNumMap terminalVar;
  private final VariableNumMap ccgCategoryVar;
  private final ParametricFactor terminalFamily;

  private final VariableNumMap terminalPosVar;
  private final VariableNumMap terminalSyntaxVar;
  private final ParametricFactor terminalPosFamily;

  private final ParametricFactor terminalSyntaxFamily;

  /**
   * Names of the parameter vectors governing each factor in the lexicon
   * entries.
   */
  public static final String TERMINAL_PARAMETERS = "terminals";
  public static final String TERMINAL_POS_PARAMETERS = "terminalPos";
  public static final String TERMINAL_SYNTAX_PARAMETERS = "terminalSyntax";

  public ParametricTableLexicon(VariableNumMap terminalVar, VariableNumMap ccgCategoryVar,
      ParametricFactor terminalFamily, VariableNumMap terminalPosVar,
      VariableNumMap terminalSyntaxVar, ParametricFactor terminalPosFamily,
      ParametricFactor terminalSyntaxFamily) {
    this.terminalVar = Preconditions.checkNotNull(terminalVar);
    this.ccgCategoryVar = Preconditions.checkNotNull(ccgCategoryVar);
    this.terminalFamily = Preconditions.checkNotNull(terminalFamily);

    this.terminalPosVar = Preconditions.checkNotNull(terminalPosVar);
    this.terminalSyntaxVar = Preconditions.checkNotNull(terminalSyntaxVar);
    this.terminalPosFamily = Preconditions.checkNotNull(terminalPosFamily);

    this.terminalSyntaxFamily = Preconditions.checkNotNull(terminalSyntaxFamily);
  }

  public VariableNumMap getTerminalVar() {
    return terminalVar;
  }

  public VariableNumMap getCcgCategoryVar() {
    return ccgCategoryVar;
  }

  public VariableNumMap getTerminalPosVar() {
    return terminalPosVar;
  }

  public VariableNumMap getTerminalSyntaxVar() {
    return terminalSyntaxVar;
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    SufficientStatistics terminalParameters = terminalFamily.getNewSufficientStatistics();
    SufficientStatistics terminalPosParameters = terminalPosFamily.getNewSufficientStatistics();
    SufficientStatistics terminalSyntaxParameters = terminalSyntaxFamily.getNewSufficientStatistics();
    
    return new ListSufficientStatistics(Arrays.asList(TERMINAL_PARAMETERS, TERMINAL_POS_PARAMETERS,
        TERMINAL_SYNTAX_PARAMETERS), Arrays.asList(terminalParameters, terminalPosParameters,
            terminalSyntaxParameters));
  }

  @Override
  public TableLexicon getModelFromParameters(SufficientStatistics parameters) {
    ListSufficientStatistics parameterList = parameters.coerceToList();
    DiscreteFactor terminalDistribution = terminalFamily.getModelFromParameters(parameterList
        .getStatisticByName(TERMINAL_PARAMETERS)).coerceToDiscrete();
    DiscreteFactor terminalPosDistribution = terminalPosFamily.getModelFromParameters(parameterList
        .getStatisticByName(TERMINAL_POS_PARAMETERS)).coerceToDiscrete();
    DiscreteFactor terminalSyntaxDistribution = terminalSyntaxFamily.getModelFromParameters(parameterList
        .getStatisticByName(TERMINAL_SYNTAX_PARAMETERS)).coerceToDiscrete();

    return new TableLexicon(terminalVar, ccgCategoryVar, terminalDistribution, terminalPosVar,
        terminalSyntaxVar, terminalPosDistribution, terminalSyntaxDistribution);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters) {
    return getParameterDescription(parameters, -1);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) {
    ListSufficientStatistics parameterList = parameters.coerceToList();
    StringBuilder sb = new StringBuilder();
    sb.append(terminalFamily.getParameterDescription(
        parameterList.getStatisticByName(TERMINAL_PARAMETERS), numFeatures));
    sb.append(terminalPosFamily.getParameterDescription(
        parameterList.getStatisticByName(TERMINAL_POS_PARAMETERS), numFeatures));
    sb.append(terminalSyntaxFamily.getParameterDescription(
        parameterList.getStatisticByName(TERMINAL_SYNTAX_PARAMETERS), numFeatures));
    return sb.toString();
  }

  @Override
  public void incrementLexiconSufficientStatistics(SufficientStatistics gradient, 
      SufficientStatistics currentParameters, CcgParse parse, double count) {
    List<LexiconEntry> lexiconEntries = parse.getSpannedLexiconEntries();
    List<String> posTags = parse.getSpannedPosTagsByLexiconEntry();
    Preconditions.checkArgument(lexiconEntries.size() == posTags.size());
    int numEntries = lexiconEntries.size();
    for (int i = 0; i < numEntries; i++) {
      LexiconEntry lexiconEntry = lexiconEntries.get(i);
      incrementLexiconEntrySufficientStatistics(gradient, currentParameters, lexiconEntry, count);
      incrementPosSufficientStatistics(gradient, currentParameters, posTags.get(i), 
          lexiconEntry.getCategory().getSyntax(), count);
      incrementLexiconSyntaxSufficientStatistics(gradient, currentParameters,
          lexiconEntry.getWords(), lexiconEntry.getCategory().getSyntax(), count);
    }
  }

  public void incrementLexiconEntrySufficientStatistics(SufficientStatistics gradient, 
      SufficientStatistics currentParameters, LexiconEntry entry, double count) {
    SufficientStatistics terminalGradient = gradient.coerceToList().getStatisticByName(TERMINAL_PARAMETERS);
    SufficientStatistics terminalParameters = currentParameters.coerceToList()
        .getStatisticByName(TERMINAL_PARAMETERS);
    Assignment assignment = Assignment.unionAll(
        terminalVar.outcomeArrayToAssignment(entry.getWords()),
        ccgCategoryVar.outcomeArrayToAssignment(entry.getCategory()));
    terminalFamily.incrementSufficientStatisticsFromAssignment(terminalGradient,
        terminalParameters, assignment, count);
  }

  public void incrementPosSufficientStatistics(SufficientStatistics gradient,
      SufficientStatistics currentParameters, String posTag,
      HeadedSyntacticCategory syntax, double count) {
    SufficientStatistics terminalPosGradient = gradient.coerceToList()
        .getStatisticByName(TERMINAL_POS_PARAMETERS);
    SufficientStatistics terminalPosParameters = currentParameters.coerceToList()
        .getStatisticByName(TERMINAL_POS_PARAMETERS);
    Assignment posAssignment = terminalPosVar.outcomeArrayToAssignment(posTag).union(
        terminalSyntaxVar.outcomeArrayToAssignment(syntax));
    terminalPosFamily.incrementSufficientStatisticsFromAssignment(terminalPosGradient,
        terminalPosParameters, posAssignment, count);
  }

  public void incrementLexiconSyntaxSufficientStatistics(SufficientStatistics gradient, 
      SufficientStatistics currentParameters, List<String> words,
      HeadedSyntacticCategory syntax, double count) {
    SufficientStatistics terminalSyntaxGradient = gradient.coerceToList()
        .getStatisticByName(TERMINAL_SYNTAX_PARAMETERS);
    SufficientStatistics terminalSyntaxParameters = currentParameters.coerceToList()
        .getStatisticByName(TERMINAL_SYNTAX_PARAMETERS);
    Assignment assignment = terminalVar.outcomeArrayToAssignment(words).union(
        terminalSyntaxVar.outcomeArrayToAssignment(syntax));
    terminalSyntaxFamily.incrementSufficientStatisticsFromAssignment(terminalSyntaxGradient,
        terminalSyntaxParameters, assignment, count);
  }
}
