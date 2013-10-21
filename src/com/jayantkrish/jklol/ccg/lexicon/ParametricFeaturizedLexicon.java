package com.jayantkrish.jklol.ccg.lexicon;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.supertag.WordAndPos;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.ListSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.models.parametric.TensorSufficientStatistics;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.sequence.ListLocalContext;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.Assignment;

public class ParametricFeaturizedLexicon implements ParametricCcgLexicon {
  private static final long serialVersionUID = 1L;
  
  private final VariableNumMap terminalVar;
  private final VariableNumMap ccgCategoryVar;
  private final ParametricFactor terminalFamily;

  private final FeatureVectorGenerator<LexiconEvent> featureGenerator;
  private final int numFeatures;
  private final VariableNumMap featureVar;

  /**
   * Names of the parameter vectors governing each factor in the lexicon
   * entries.
   */
  public static final String TERMINAL_PARAMETERS = "terminals";
  public static final String TERMINAL_FEATURE_PARAMETERS = "terminalFeatures";
  
  public static final String TERMINAL_FEATURE_VAR_NAME = "terminalFeaturesVar";

  public ParametricFeaturizedLexicon(VariableNumMap terminalVar,
      VariableNumMap ccgCategoryVar, ParametricFactor terminalFamily,
      FeatureVectorGenerator<LexiconEvent> featureGenerator) {
    this.terminalVar = Preconditions.checkNotNull(terminalVar);
    this.ccgCategoryVar = Preconditions.checkNotNull(ccgCategoryVar);
    this.terminalFamily = Preconditions.checkNotNull(terminalFamily);
    this.featureGenerator = Preconditions.checkNotNull(featureGenerator);
    this.numFeatures = featureGenerator.getNumberOfFeatures();
    this.featureVar = VariableNumMap.singleton(0, TERMINAL_FEATURE_VAR_NAME, featureGenerator.getFeatureDictionary());
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    SufficientStatistics terminalParameters = terminalFamily.getNewSufficientStatistics();
    SufficientStatistics terminalFeatureParameters = TensorSufficientStatistics.createDense(featureVar, 
        new DenseTensorBuilder(new int[] {0}, new int[] {numFeatures}));

    return new ListSufficientStatistics(Arrays.asList(TERMINAL_PARAMETERS, TERMINAL_FEATURE_PARAMETERS),
        Arrays.asList(terminalParameters, terminalFeatureParameters));
  }

  @Override
  public CcgLexicon getModelFromParameters(SufficientStatistics parameters) {
    ListSufficientStatistics parameterList = parameters.coerceToList();
    DiscreteFactor terminalDistribution = terminalFamily.getModelFromParameters(parameterList
        .getStatisticByName(TERMINAL_PARAMETERS)).coerceToDiscrete();
    DiscreteFactor terminalFeatureDistribution = ((TensorSufficientStatistics) parameterList
        .getStatisticByName(TERMINAL_FEATURE_PARAMETERS)).getFactor();

   return new FeaturizedLexicon(terminalVar, ccgCategoryVar, terminalDistribution,
       featureGenerator, terminalFeatureDistribution);
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
    
    DiscreteFactor terminalFeatureDistribution = ((TensorSufficientStatistics) parameterList
        .getStatisticByName(TERMINAL_FEATURE_PARAMETERS)).getFactor();
    sb.append(terminalFeatureDistribution.describeAssignments(terminalFeatureDistribution
        .getMostLikelyAssignments(numFeatures)));
    return sb.toString();
  }

  @Override
  public void incrementLexiconSufficientStatistics(SufficientStatistics gradient, CcgParse parse,
      double count) {
    SufficientStatistics terminalGradient = gradient.coerceToList().getStatisticByName(TERMINAL_PARAMETERS);
    TensorSufficientStatistics featureGradient = (TensorSufficientStatistics) gradient.coerceToList()
        .getStatisticByName(TERMINAL_FEATURE_PARAMETERS);
    
    List<String> words = parse.getSpannedWords();
    List<String> posTags = parse.getSpannedPosTags();
    List<WordAndPos> wordAndPosList = WordAndPos.createExample(words, posTags);
    List<Integer> wordIndexes = parse.getWordIndexesWithLexiconEntries();
    List<LexiconEntry> lexiconEntries = parse.getSpannedLexiconEntries();
    Preconditions.checkArgument(wordIndexes.size() == lexiconEntries.size());
    for (int i = 0; i < lexiconEntries.size(); i++) {
      CcgCategory ccgCategory = lexiconEntries.get(i).getCategory();
      List<String> terminals = lexiconEntries.get(i).getWords();
      // Update the word -> ccg category indicator features.
      Assignment assignment = Assignment.unionAll(
          terminalVar.outcomeArrayToAssignment(terminals),
          ccgCategoryVar.outcomeArrayToAssignment(ccgCategory));
      terminalFamily.incrementSufficientStatisticsFromAssignment(terminalGradient,
          assignment, count);

      // Update the feature weights for the generated feature vectors.
      int sentenceIndex = wordIndexes.get(i);
      LexiconEvent event = new LexiconEvent(ccgCategory, terminals,
          new ListLocalContext<WordAndPos>(wordAndPosList, sentenceIndex));
      Tensor featureWeights = featureGenerator.apply(event);

      featureGradient.increment(featureWeights, count);
    }
  }
}
