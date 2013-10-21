package com.jayantkrish.jklol.ccg.lexicon;

import java.util.Map;

import com.google.common.collect.Maps;
import com.jayantkrish.jklol.preprocessing.FeatureGenerator;

public class LexiconFeatureGenerators {
  public static class PosFeatureGenerator implements FeatureGenerator<LexiconEvent, String> {
    private static final long serialVersionUID = 1L;

    @Override
    public Map<String, Double> generateFeatures(LexiconEvent item) {
      Map<String, Double> features = Maps.newHashMap();
      StringBuilder sb = new StringBuilder();
      sb.append("POS=");
      sb.append(item.getContext().getItem().getPos());
      sb.append("_SYNTAX=");
      sb.append(item.getCategory().getSyntax());
      
      features.put(sb.toString(), 1.0);
      return features;
    }
  }

  private LexiconFeatureGenerators() {
    // Prevent instantiation.
  }
}
