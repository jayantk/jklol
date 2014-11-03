package com.jayantkrish.jklol.ccg.supertag;

import java.util.Map;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.preprocessing.FeatureGenerator;
import com.jayantkrish.jklol.sequence.LocalContext;

/**
 * Generates features based on the part-of-speech tags of the surrounding 
 * words in a sentence.
 * 
 * @author jayant
 */
public class PosContextFeatureGenerator implements FeatureGenerator<LocalContext<WordAndPos>, String> {
  private static final long serialVersionUID = 1L;
  
  private final int[][] offsets;

  public PosContextFeatureGenerator(int[][] offsets) {
    this.offsets = Preconditions.checkNotNull(offsets);
  }

  @Override
  public Map<String, Double> generateFeatures(LocalContext<WordAndPos> item) {
    Map<String, Double> weights = Maps.newHashMap();

    for (int j = 0; j < offsets.length; j++) {
      StringBuilder featureBuilder = new StringBuilder();
      featureBuilder.append("POS_");
      featureBuilder.append(Joiner.on("_").join(Ints.asList(offsets[j])));
      featureBuilder.append("=");
      for (int i = 0; i < offsets[j].length; i++) {
        WordAndPos word = item.getItem(offsets[j][i], WordAndPosFeatureGenerator.END_FUNCTION);
        featureBuilder.append(word.getPos());
        
        if (i != offsets[j].length - 1) {
          featureBuilder.append("_");
        }
      }
      String featureName = featureBuilder.toString().intern();
      weights.put(featureName, 1.0);
    }
    return weights;
  }
}
