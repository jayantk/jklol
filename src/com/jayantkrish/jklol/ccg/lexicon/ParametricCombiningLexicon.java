package com.jayantkrish.jklol.ccg.lexicon;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.ListSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

public class ParametricCombiningLexicon implements ParametricCcgLexicon {
  private static final long serialVersionUID = 1L;

  private final VariableNumMap terminalVar;
  private final List<String> lexiconNames;
  private final List<ParametricCcgLexicon> lexicons;
  
  public ParametricCombiningLexicon(VariableNumMap terminalVar, List<String> lexiconNames,
      List<ParametricCcgLexicon> lexicons) {
    this.terminalVar = Preconditions.checkNotNull(terminalVar);
    this.lexiconNames = ImmutableList.copyOf(lexiconNames);
    this.lexicons = ImmutableList.copyOf(lexicons);
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    List<SufficientStatistics> params = Lists.newArrayList();
    for (ParametricCcgLexicon lexicon : lexicons) {
      params.add(lexicon.getNewSufficientStatistics());
    }
    return new ListSufficientStatistics(lexiconNames, params);
  }

  @Override
  public CcgLexicon getModelFromParameters(SufficientStatistics parameters) {
    ListSufficientStatistics params = parameters.coerceToList();
    List<CcgLexicon> instantiatedLexicons = Lists.newArrayList();
    for (int i = 0; i < lexicons.size(); i++) {
      ParametricCcgLexicon lexicon = lexicons.get(i);
      SufficientStatistics lexiconParams = params.getStatisticByName(lexiconNames.get(i)); 
      instantiatedLexicons.add(lexicon.getModelFromParameters(lexiconParams));
    }
    
    return new CombiningLexicon(terminalVar, instantiatedLexicons);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters) {
    return getParameterDescription(parameters, -1);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) {
    StringBuilder sb = new StringBuilder();
    ListSufficientStatistics params = parameters.coerceToList();
    for (int i = 0; i < lexicons.size(); i++) {
      ParametricCcgLexicon lexicon = lexicons.get(i);
      SufficientStatistics lexiconParams = params.getStatisticByName(lexiconNames.get(i));
      sb.append(lexicon.getParameterDescription(lexiconParams, numFeatures));
    }
    return sb.toString();
  }

  @Override
  public void incrementLexiconSufficientStatistics(SufficientStatistics gradient,
      SufficientStatistics currentParameters, CcgParse parse, double count) {
    ListSufficientStatistics gradientList = gradient.coerceToList();
    ListSufficientStatistics currentParametersList = currentParameters.coerceToList();

    for (int i = 0; i < lexicons.size(); i++) {
      ParametricCcgLexicon lexicon = lexicons.get(i);
      SufficientStatistics lexiconGradient = gradientList.getStatisticByName(lexiconNames.get(i));
      SufficientStatistics lexiconCurrentParameters = currentParametersList.getStatisticByName(lexiconNames.get(i));

      lexicon.incrementLexiconSufficientStatistics(lexiconGradient, lexiconCurrentParameters, parse, count);
    }
  }
}
