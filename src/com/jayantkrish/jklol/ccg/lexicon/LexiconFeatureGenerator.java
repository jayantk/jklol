package com.jayantkrish.jklol.ccg.lexicon;

import java.util.Map;

import com.google.common.collect.Maps;
import com.jayantkrish.jklol.ccg.supertag.WordAndPos;
import com.jayantkrish.jklol.preprocessing.FeatureGenerator;
import com.jayantkrish.jklol.sequence.LocalContext;

public class LexiconFeatureGenerator implements FeatureGenerator<LocalContext<WordAndPos>, String> {
  private static final long serialVersionUID = 1L;

  @Override
  public Map<String, Double> generateFeatures(LocalContext<WordAndPos> item) {
    Map<String, Double> featureWeights = Maps.newHashMap();
    String featureName = ("POS_" + item.getItem().getPos()).intern();
    featureWeights.put(featureName, 1.0);
    return featureWeights;
  }
}
