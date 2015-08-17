package com.jayantkrish.jklol.ccg.lexicon;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.models.ClassifierFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Lexicon scoring function for that applies a linear classifier
 * to features generated from spans of the input sentence. The
 * feature generation function is supplied by the user. 
 *  
 * @author jayant
 *
 */
public class FeaturizedLexiconScorer implements LexiconScorer {
  private static final long serialVersionUID = 5L;

  private final String featureVectorAnnotationName;
  
  private final VariableNumMap labelVar;
  private final VariableNumMap featureVectorVar;
  private final ClassifierFactor featureWeights;
  private final Function<CcgCategory, ?> categoryToLabel;

  public FeaturizedLexiconScorer(String featureVectorAnnotationName, VariableNumMap labelVar,
      VariableNumMap featureVectorVar, ClassifierFactor featureWeights,
      Function<CcgCategory, ?> categoryToLabel) {
    this.featureVectorAnnotationName = Preconditions.checkNotNull(featureVectorAnnotationName);

    this.labelVar = Preconditions.checkNotNull(labelVar);
    this.featureVectorVar = Preconditions.checkNotNull(featureVectorVar);
    this.featureWeights = Preconditions.checkNotNull(featureWeights);
    this.categoryToLabel = categoryToLabel;
  }

  @Override
  public double getCategoryWeight(int spanStart, int spanEnd, AnnotatedSentence sentence,
      CcgCategory category) {
    SpanFeatureAnnotation annotation = (SpanFeatureAnnotation) sentence.getAnnotation(
        featureVectorAnnotationName);
    Preconditions.checkNotNull(annotation, "Sentence was not annotated with features: " + sentence);

    Tensor featureVector = annotation.getFeatureVector(spanStart, spanEnd);

    Assignment labelAssignment = labelVar.outcomeArrayToAssignment(categoryToLabel.apply(category));
    if (labelVar.isValidAssignment(labelAssignment)) {
      Assignment assignment = featureVectorVar.outcomeArrayToAssignment(featureVector)
          .union(labelAssignment);
      return featureWeights.getUnnormalizedProbability(assignment);
    } else {
      return 1.0;
    }
  }
}