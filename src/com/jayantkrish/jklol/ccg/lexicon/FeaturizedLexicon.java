package com.jayantkrish.jklol.ccg.lexicon;

import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.supertag.WordAndPos;
import com.jayantkrish.jklol.models.ClassifierFactor;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.sequence.LocalContext;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.Assignment;

/**
 * CCG lexicon which uses a given feature set to determine the
 * weight of a lexicon entry.
 *
 * @author jayant
 */
public class FeaturizedLexicon extends AbstractCcgLexicon {
  private static final long serialVersionUID = 1L;

  private final VariableNumMap ccgSyntaxVar;
  private final VariableNumMap featureVectorVar;
  private final ClassifierFactor featureWeights;

  public FeaturizedLexicon(VariableNumMap terminalVar, VariableNumMap ccgCategoryVar,
      DiscreteFactor terminalDistribution, FeatureVectorGenerator<LocalContext<WordAndPos>> featureGenerator,
      VariableNumMap ccgSyntaxVar, VariableNumMap featureVectorVar, ClassifierFactor featureWeights) {
    super(terminalVar, ccgCategoryVar, terminalDistribution, featureGenerator);
    
    this.ccgSyntaxVar = Preconditions.checkNotNull(ccgSyntaxVar);
    this.featureVectorVar = Preconditions.checkNotNull(featureVectorVar);
    this.featureWeights = Preconditions.checkNotNull(featureWeights);
    Preconditions.checkArgument(((long) featureGenerator.getNumberOfFeatures()) 
        == featureWeights.getInputVariable().getNumberOfPossibleAssignments());
  }

  @Override
  protected double getCategoryWeight(List<String> originalWords, List<String> preprocessedWords,
      List<String> pos, List<WordAndPos> ccgWordList, List<Tensor> featureVectors, int spanStart,
      int spanEnd, List<String> terminals, CcgCategory category) {
    Tensor featureValues = featureVectors.get(spanEnd);
    Assignment assignment = featureVectorVar.outcomeArrayToAssignment(featureValues)
        .union(ccgSyntaxVar.outcomeArrayToAssignment(category.getSyntax()));
    return featureWeights.getUnnormalizedProbability(assignment);
  }
}