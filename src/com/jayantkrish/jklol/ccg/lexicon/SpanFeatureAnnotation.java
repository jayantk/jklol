package com.jayantkrish.jklol.ccg.lexicon;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.tensor.Tensor;

/**
 * Sentence annotation mapping each sentence span to
 * a feature vector.
 * 
 * @author jayantk
 *
 */
public class SpanFeatureAnnotation {
  private final Tensor[] featureVectors;
  private final int length;
  
  public SpanFeatureAnnotation(Tensor[] featureVectors, int length) {
    this.featureVectors = Preconditions.checkNotNull(featureVectors);
    this.length = length;
    Preconditions.checkArgument(featureVectors.length == length * (length + 1) / 2);
  }

  /**
   * Annotates the spans of {@code sentence} with feature vectors
   * created by applying {@code featureGenerator}.
   * 
   * @param sentence
   * @param featureGenerator
   * @return
   */
  public static SpanFeatureAnnotation annotate(AnnotatedSentence sentence,
      FeatureVectorGenerator<StringContext> featureGenerator) {
    int numTerminals = sentence.size();
    Tensor[] featureVectors = new Tensor[numTerminals * (numTerminals + 1) / 2];
    for (int i = 0; i < numTerminals; i++) {
      for (int j = i; j < numTerminals; j++) {
        int spanIndex = getSpanIndex(i, j, numTerminals);
        featureVectors[spanIndex] = featureGenerator.apply(
            new StringContext(i, j, sentence));
      }
    }
    return new SpanFeatureAnnotation(featureVectors, numTerminals);
  }

  public Tensor getFeatureVector(int spanStart, int spanEnd) {
    int spanIndex = getSpanIndex(spanStart, spanEnd, length);
    if (spanIndex < featureVectors.length) {
      return featureVectors[spanIndex];
    } else {
      return null;
    }
  }

  private static final int getSpanIndex(int spanStart, int spanEnd, int numTerminals) {
    return (spanStart * numTerminals) - (spanStart * (spanStart - 1) / 2)
        + (spanEnd - spanStart);
  }
}
