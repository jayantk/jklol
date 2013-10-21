package com.jayantkrish.jklol.ccg.lexicon;

import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.supertag.WordAndPos;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.sequence.ListLocalContext;
import com.jayantkrish.jklol.tensor.Tensor;

/**
 * CCG lexicon which uses a given feature set to determine the
 * weight of a lexicon entry.
 *
 * @author jayant
 */
public class FeaturizedLexicon extends AbstractCcgLexicon {
  private static final long serialVersionUID = 1L;

  private final FeatureVectorGenerator<LexiconEvent> featureGenerator;
  private final DiscreteFactor featureWeights;

  public FeaturizedLexicon(VariableNumMap terminalVar, VariableNumMap ccgCategoryVar,
      DiscreteFactor terminalDistribution, FeatureVectorGenerator<LexiconEvent> featureGenerator,
      DiscreteFactor featureWeights) {
    super(terminalVar, ccgCategoryVar, terminalDistribution);
    
    this.featureGenerator = Preconditions.checkNotNull(featureGenerator);
    this.featureWeights = Preconditions.checkNotNull(featureWeights);
    Preconditions.checkArgument(((long) featureGenerator.getNumberOfFeatures()) 
        == featureWeights.getWeights().getMaxKeyNum());
  }

  @Override
  protected double getCategoryWeight(List<String> words, List<String> pos,
      int spanStart, int spanEnd, List<String> terminals, CcgCategory category) {
    List<WordAndPos> wordAndPosList = WordAndPos.createExample(words, pos);
    LexiconEvent event = new LexiconEvent(category, terminals, new ListLocalContext<WordAndPos>(wordAndPosList, spanEnd));
    
    Tensor featureValues = featureGenerator.apply(event);
    return featureValues.innerProduct(featureWeights.getWeights()).getByDimKey();
  }
}