package com.jayantkrish.jklol.ccg.lexicon;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.models.ClassifierFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.loglinear.ParametricLinearClassifierFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.Assignment;

public class ParametricFeaturizedLexiconScorer implements ParametricLexiconScorer {
  private static final long serialVersionUID = 2L;

  private final String featureVectorAnnotationName;
  private final VariableNumMap labelVar;
  private final VariableNumMap featureVectorVar;
  private final ParametricLinearClassifierFactor classifierFamily;
  private final Function<CcgCategory, Object> categoryToLabel;

  /**
   * Names of the parameter vectors governing each factor in the lexicon
   * entries.
   */
  public static final String TERMINAL_PARAMETERS = "terminals";
  public static final String TERMINAL_FEATURE_PARAMETERS = "terminalFeatures";
  
  public static final String TERMINAL_FEATURE_VAR_NAME = "terminalFeaturesVar";

  public ParametricFeaturizedLexiconScorer(String featureVectorAnnotationName,
      VariableNumMap labelVar, VariableNumMap featureVectorVar,
      ParametricLinearClassifierFactor classifierFamily, Function<CcgCategory, Object> categoryToLabel) {
    this.featureVectorAnnotationName = Preconditions.checkNotNull(featureVectorAnnotationName);
    this.labelVar = Preconditions.checkNotNull(labelVar);
    this.featureVectorVar = Preconditions.checkNotNull(featureVectorVar);
    this.classifierFamily = Preconditions.checkNotNull(classifierFamily);
    this.categoryToLabel = categoryToLabel;
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    return classifierFamily.getNewSufficientStatistics();
  }

  @Override
  public FeaturizedLexiconScorer getModelFromParameters(SufficientStatistics parameters) {
    ClassifierFactor classifier = classifierFamily.getModelFromParameters(parameters);

   return new FeaturizedLexiconScorer(featureVectorAnnotationName, labelVar,
       featureVectorVar, classifier, categoryToLabel);
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
      AnnotatedSentence sentence, CcgCategory category, double count) {

    SpanFeatureAnnotation annotation = (SpanFeatureAnnotation) sentence
        .getAnnotation(featureVectorAnnotationName);
    Tensor featureVector = annotation.getFeatureVector(spanStart, spanEnd);
    Object label = categoryToLabel.apply(category);

    if (labelVar.isValidOutcomeArray(label)) {
      Assignment assignment = labelVar.outcomeArrayToAssignment(label)
          .union(featureVectorVar.outcomeArrayToAssignment(featureVector));
      classifierFamily.incrementSufficientStatisticsFromAssignment(gradient, currentParameters,
          assignment, count);
    }
  }
}
