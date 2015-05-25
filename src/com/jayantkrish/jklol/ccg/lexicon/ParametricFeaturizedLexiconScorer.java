package com.jayantkrish.jklol.ccg.lexicon;

import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.lexicon.FeaturizedLexiconScorer.StringContext;
import com.jayantkrish.jklol.models.ClassifierFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.loglinear.ParametricLinearClassifierFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.Assignment;

public class ParametricFeaturizedLexiconScorer implements ParametricLexiconScorer {
  private static final long serialVersionUID = 2L;

  private final FeatureVectorGenerator<StringContext> featureGenerator;
  private final VariableNumMap syntaxVar;
  private final VariableNumMap featureVectorVar;
  private final ParametricLinearClassifierFactor classifierFamily;

  /**
   * Names of the parameter vectors governing each factor in the lexicon
   * entries.
   */
  public static final String TERMINAL_PARAMETERS = "terminals";
  public static final String TERMINAL_FEATURE_PARAMETERS = "terminalFeatures";
  
  public static final String TERMINAL_FEATURE_VAR_NAME = "terminalFeaturesVar";

  public ParametricFeaturizedLexiconScorer(FeatureVectorGenerator<StringContext> featureGenerator,
      VariableNumMap syntaxVar, VariableNumMap featureVectorVar,
      ParametricLinearClassifierFactor classifierFamily) {
    this.featureGenerator = Preconditions.checkNotNull(featureGenerator);
    this.syntaxVar = Preconditions.checkNotNull(syntaxVar);
    this.featureVectorVar = Preconditions.checkNotNull(featureVectorVar);
    this.classifierFamily = Preconditions.checkNotNull(classifierFamily);
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    return classifierFamily.getNewSufficientStatistics();
  }

  @Override
  public FeaturizedLexiconScorer getModelFromParameters(SufficientStatistics parameters) {
    ClassifierFactor classifier = classifierFamily.getModelFromParameters(parameters);

   return new FeaturizedLexiconScorer(featureGenerator, syntaxVar, featureVectorVar,
       classifier);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters) {
    return getParameterDescription(parameters, -1);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) {
    return classifierFamily.getParameterDescription(parameters, numFeatures);
  }

  @Override
  public void incrementLexiconSufficientStatistics(SufficientStatistics gradient,
      SufficientStatistics currentParameters, int spanStart, int spanEnd,
      List<String> sentenceWords, List<String> sentencePreprocessedWords,
      List<String> sentencePos, List<String> wordSequence, List<String> posSequence,
      CcgCategory category, double count) {

    StringContext context = new StringContext(spanStart, spanEnd, sentenceWords,
        sentencePos);
    Tensor featureVector = featureGenerator.apply(context);
    HeadedSyntacticCategory syntax = category.getSyntax();
    
    Assignment assignment = syntaxVar.outcomeArrayToAssignment(syntax)
        .union(featureVectorVar.outcomeArrayToAssignment(featureVector));

    classifierFamily.incrementSufficientStatisticsFromAssignment(gradient, currentParameters,
        assignment, count);
  }
}
