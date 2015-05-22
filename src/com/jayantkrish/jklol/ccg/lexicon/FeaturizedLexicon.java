package com.jayantkrish.jklol.ccg.lexicon;

import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.models.ClassifierFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Scoring function for lexicon entries that applies a
 * user supplied feature generation function to the input
 * sentence. 
 *  
 * @author jayant
 *
 */
public class FeaturizedLexicon implements LexiconScorer {
  private static final long serialVersionUID = 4L;

  private final FeatureVectorGenerator<StringContext> featureGenerator;

  private final VariableNumMap syntaxVar;
  private final VariableNumMap featureVectorVar;
  private final ClassifierFactor featureWeights;

  public FeaturizedLexicon(FeatureVectorGenerator<StringContext> featureGenerator,
      VariableNumMap syntaxVar, VariableNumMap featureVectorVar, ClassifierFactor featureWeights) {
    this.featureGenerator = Preconditions.checkNotNull(featureGenerator);
    
    this.syntaxVar = Preconditions.checkNotNull(syntaxVar);
    this.featureVectorVar = Preconditions.checkNotNull(featureVectorVar);
    this.featureWeights = Preconditions.checkNotNull(featureWeights);
    Preconditions.checkArgument(((long) featureGenerator.getNumberOfFeatures()) 
        == featureWeights.getInputVariable().getNumberOfPossibleAssignments());
  }

  @Override
  public InstantiatedLexiconScorer get(List<String> terminals, List<String> preprocessedTerminals,
      List<String> posTags) {
    int numTerminals = terminals.size();
    Tensor[] featureVectors = new Tensor[numTerminals * numTerminals];
    for (int i = 0; i < numTerminals; i++) {
      for (int j = i; j < numTerminals; j++) {
        int spanIndex = getSpanIndex(i, j, numTerminals);
        featureVectors[spanIndex] = featureGenerator.apply(
            new StringContext(i, j, terminals, preprocessedTerminals, posTags));
      }
    }

    return new FeatureInstantiatedLexiconScorer(featureVectors, numTerminals,
        syntaxVar, featureVectorVar, featureWeights);
  }

  public static final int getSpanIndex(int spanStart, int spanEnd, int numTerminals) {
    return (spanStart * numTerminals) + (spanEnd - spanStart);
  }

  public static class FeatureInstantiatedLexiconScorer implements InstantiatedLexiconScorer {
    private final Tensor[] featureVectors;
    private final int numTerminals;

    private final VariableNumMap syntaxVar;
    private final VariableNumMap featureVectorVar;

    private final ClassifierFactor featureWeights;
    
    public FeatureInstantiatedLexiconScorer(Tensor[] featureVectors, int numTerminals,
        VariableNumMap syntaxVar, VariableNumMap featureVectorVar,
        ClassifierFactor featureWeights) {
      this.featureVectors = Preconditions.checkNotNull(featureVectors);
      this.numTerminals = numTerminals;

      this.syntaxVar = Preconditions.checkNotNull(syntaxVar);
      this.featureVectorVar = Preconditions.checkNotNull(featureVectorVar);
      this.featureWeights = Preconditions.checkNotNull(featureWeights);
    }

    @Override
    public double getCategoryWeight(int spanStart, int spanEnd, List<String> terminalValue,
        List<String> posTags, CcgCategory category) {
      int spanIndex = FeaturizedLexicon.getSpanIndex(spanStart, spanEnd, numTerminals);
      Tensor featureVector = featureVectors[spanIndex];
      
      Assignment syntaxAssignment = syntaxVar.outcomeArrayToAssignment(category.getSyntax());
      Assignment assignment = featureVectorVar.outcomeArrayToAssignment(featureVector)
          .union(syntaxAssignment);
      return featureWeights.getUnnormalizedProbability(assignment);
    }
  }
  
  /**
   * A string span within a larger sentence. This class is used
   * for generating feature vectors.
   * 
   * @author jayant
   *
   */
  public static class StringContext {
    private final int spanStart;
    private final int spanEnd;
    
    private final List<String> originalWords;
    private final List<String> preprocessedWords;
    private final List<String> pos;

    public StringContext(int spanStart, int spanEnd, List<String> originalWords,
        List<String> preprocessedWords, List<String> pos) {
      this.spanStart = spanStart;
      this.spanEnd = spanEnd;

      this.originalWords = originalWords;
      this.preprocessedWords = preprocessedWords;
      this.pos = pos;
    }

    public int getSpanStart() {
      return spanStart;
    }

    public int getSpanEnd() {
      return spanEnd;
    }

    public List<String> getOriginalWords() {
      return originalWords;
    }

    /**
     * Gets the words in the sentence after preprocessing
     * (e.g., lowercasing)
     * 
     * @return
     */
    public List<String> getPreprocessedWords() {
      return preprocessedWords;
    }

    public List<String> getPos() {
      return pos;
    }
  }
}