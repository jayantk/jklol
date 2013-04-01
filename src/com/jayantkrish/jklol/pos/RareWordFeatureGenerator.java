package com.jayantkrish.jklol.pos;

import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.pos.PosTaggedSentence.LocalContext;
import com.jayantkrish.jklol.preprocessing.FeatureGenerator;

/**
 * Feature generator that dispatches to different feature generators
 * based on whether the given context contains a rare word or not.
 *    
 * @author jayant
 */
public class RareWordFeatureGenerator implements FeatureGenerator<LocalContext, String> {
  private static final long serialVersionUID = 1L;
  
  private final Set<String> commonWords;
  private final FeatureGenerator<LocalContext, String> commonWordGenerator;
  private final FeatureGenerator<LocalContext, String> rareWordGenerator;
 
  public RareWordFeatureGenerator(Set<String> commonWords, 
      FeatureGenerator<LocalContext, String> commonWordGenerator,
      FeatureGenerator<LocalContext, String> rareWordGenerator) {
    this.commonWords = Preconditions.checkNotNull(commonWords);
    this.commonWordGenerator = Preconditions.checkNotNull(commonWordGenerator);
    this.rareWordGenerator = Preconditions.checkNotNull(rareWordGenerator);
  }

  @Override
  public Map<String, Double> generateFeatures(LocalContext item) {
    if (commonWords.contains(item.getWord())) {
      return commonWordGenerator.generateFeatures(item);
    } else {
      return rareWordGenerator.generateFeatures(item);
    }
  }
}
