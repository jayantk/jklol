package com.jayantkrish.jklol.pos;

import java.util.Map;

import com.google.common.collect.Maps;
import com.jayantkrish.jklol.pos.PosTaggedSentence.LocalContext;
import com.jayantkrish.jklol.preprocessing.FeatureGenerator;

public class WordContextFeatureGenerator implements FeatureGenerator<LocalContext, String> {

  private static final long serialVersionUID = 1L;

  @Override
  public Map<String, Double> generateFeatures(LocalContext item) {
    Map<String, Double> weights = Maps.newHashMap();
    for (String word : item.getWords()) {
      weights.put(formatFeature(word, item.getPos()), 1.0);
    }
    weights.put("bias", 1.0);
    return weights;
  }

  private static String formatFeature(String word, String pos) {
    String featureName = "WORD=" + word;
    featureName = featureName.intern();
    return featureName;
  }
}
