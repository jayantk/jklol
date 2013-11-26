package com.jayantkrish.jklol.ccg.lexicon;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.supertag.SupertaggedSentence;
import com.jayantkrish.jklol.ccg.supertag.WordAndPos;
import com.jayantkrish.jklol.models.ClassifierFactor;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.loglinear.ConditionalLogLinearFactor;
import com.jayantkrish.jklol.models.parametric.ListSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.sequence.ListLocalContext;
import com.jayantkrish.jklol.sequence.LocalContext;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.Assignment;

public class ParametricFeaturizedLexicon<T extends SupertaggedSentence> implements ParametricCcgLexicon<T> {
  private static final long serialVersionUID = 1L;
  
  private final VariableNumMap terminalVar;
  private final VariableNumMap ccgCategoryVar;
  private final ParametricFactor terminalFamily;

  private final FeatureVectorGenerator<LocalContext<WordAndPos>> featureGenerator;
  private final VariableNumMap ccgSyntaxVar;
  private final VariableNumMap featureVar;
  private final ConditionalLogLinearFactor featureFamily;

  /**
   * Names of the parameter vectors governing each factor in the lexicon
   * entries.
   */
  public static final String TERMINAL_PARAMETERS = "terminals";
  public static final String TERMINAL_FEATURE_PARAMETERS = "terminalFeatures";
  
  public static final String TERMINAL_FEATURE_VAR_NAME = "terminalFeaturesVar";

  public ParametricFeaturizedLexicon(VariableNumMap terminalVar,
      VariableNumMap ccgCategoryVar, ParametricFactor terminalFamily,
      FeatureVectorGenerator<LocalContext<WordAndPos>> featureGenerator,
      VariableNumMap ccgSyntaxVar, VariableNumMap featureVar, ConditionalLogLinearFactor featureFamily) {
    this.terminalVar = Preconditions.checkNotNull(terminalVar);
    this.ccgCategoryVar = Preconditions.checkNotNull(ccgCategoryVar);
    this.terminalFamily = Preconditions.checkNotNull(terminalFamily);
    this.featureGenerator = Preconditions.checkNotNull(featureGenerator);
    this.ccgSyntaxVar = Preconditions.checkNotNull(ccgSyntaxVar);
    this.featureVar = Preconditions.checkNotNull(featureVar);
    this.featureFamily = Preconditions.checkNotNull(featureFamily);
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    SufficientStatistics terminalParameters = terminalFamily.getNewSufficientStatistics();
    SufficientStatistics terminalFeatureParameters = featureFamily.getNewSufficientStatistics(); 

    return new ListSufficientStatistics(Arrays.asList(TERMINAL_PARAMETERS, TERMINAL_FEATURE_PARAMETERS),
        Arrays.asList(terminalParameters, terminalFeatureParameters));
  }

  @Override
  public CcgLexicon<T> getModelFromParameters(SufficientStatistics parameters) {
    ListSufficientStatistics parameterList = parameters.coerceToList();
    DiscreteFactor terminalDistribution = terminalFamily.getModelFromParameters(parameterList
        .getStatisticByName(TERMINAL_PARAMETERS)).coerceToDiscrete();
    ClassifierFactor terminalFeatureDistribution = featureFamily.getModelFromParameters(parameterList
        .getStatisticByName(TERMINAL_FEATURE_PARAMETERS));

   return new FeaturizedLexicon<T>(terminalVar, ccgCategoryVar, terminalDistribution,
       featureGenerator, ccgSyntaxVar, featureVar, terminalFeatureDistribution);
  }

  @Override
  public ParametricFeaturizedLexicon<T> rescaleFeatures(SufficientStatistics rescaling) {
    if (rescaling == null) {
      return this;
    }

    ListSufficientStatistics rescalingList = rescaling.coerceToList();
    ParametricFactor newTerminalFamily = terminalFamily.rescaleFeatures(rescalingList
        .getStatisticByName(TERMINAL_PARAMETERS));
    ConditionalLogLinearFactor newFeatureFamily = featureFamily.rescaleFeatures(rescalingList
        .getStatisticByName(TERMINAL_FEATURE_PARAMETERS));

    return new ParametricFeaturizedLexicon<T>(terminalVar, ccgCategoryVar, newTerminalFamily,
        featureGenerator, ccgSyntaxVar, featureVar, newFeatureFamily);
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
    sb.append(featureFamily.getParameterDescription(
        parameterList.getStatisticByName(TERMINAL_FEATURE_PARAMETERS), numFeatures));
    return sb.toString();
  }

  @Override
  public void incrementLexiconSufficientStatistics(SufficientStatistics gradient, CcgParse parse,
      double count) {
    SufficientStatistics terminalGradient = gradient.coerceToList().getStatisticByName(TERMINAL_PARAMETERS);
    SufficientStatistics featureGradient = gradient.coerceToList().getStatisticByName(TERMINAL_FEATURE_PARAMETERS);

    List<String> originalWords = parse.getSpannedWords();
    List<String> posTags = parse.getSpannedPosTags();
    List<WordAndPos> wordAndPosList = WordAndPos.createExample(originalWords, posTags);
    List<Integer> wordIndexes = parse.getWordIndexesWithLexiconEntries();
    List<LexiconEntry> lexiconEntries = parse.getSpannedLexiconEntries();
    Preconditions.checkArgument(wordIndexes.size() == lexiconEntries.size());
    for (int i = 0; i < lexiconEntries.size(); i++) {
      CcgCategory ccgCategory = lexiconEntries.get(i).getCategory();
      List<String> terminals = lexiconEntries.get(i).getWords();
      // Update the word -> ccg category features.
      Assignment assignment = Assignment.unionAll(
          terminalVar.outcomeArrayToAssignment(terminals),
          ccgCategoryVar.outcomeArrayToAssignment(ccgCategory));
      terminalFamily.incrementSufficientStatisticsFromAssignment(terminalGradient,
          assignment, count);

      // Update the feature weights for the generated feature vectors.
      int sentenceIndex = wordIndexes.get(i);
      LocalContext<WordAndPos> context = new ListLocalContext<WordAndPos>(wordAndPosList, sentenceIndex);
      Tensor featureWeights = featureGenerator.apply(context);

      assignment = Assignment.unionAll(
          ccgSyntaxVar.outcomeArrayToAssignment(lexiconEntries.get(i).getCategory().getSyntax()),
          featureVar.outcomeArrayToAssignment(featureWeights));
      featureFamily.incrementSufficientStatisticsFromAssignment(featureGradient, assignment, count);
    }
  }
}
