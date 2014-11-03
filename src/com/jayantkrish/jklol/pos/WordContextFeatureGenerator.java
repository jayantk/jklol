package com.jayantkrish.jklol.pos;

import java.util.Map;
import java.util.Set;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.preprocessing.FeatureGenerator;
import com.jayantkrish.jklol.sequence.LocalContext;
import com.jayantkrish.jklol.util.ArrayUtils;

/**
 * Creates indicator features for words at various positions
 * surrounding the target word.
 * 
 * @author jayant
 */
public class WordContextFeatureGenerator implements FeatureGenerator<LocalContext<String>, String> {

  private static final long serialVersionUID = 1L;

  private final int[] offsets;
  private final Set<String> commonWords;
  
  private static final Function<Integer, String> endFunction = new Function<Integer, String>() {
    @Override
    public String apply(Integer index) {
      if (index <= 0) {
        return ("<START_" + index + ">").intern();
      } else {
        return ("<END_" + index + ">").intern();
      }
    }
  };

  public WordContextFeatureGenerator(int[] offsets, Set<String> commonWords) {
    this.offsets = ArrayUtils.copyOf(offsets, offsets.length);
    this.commonWords = ImmutableSet.copyOf(commonWords);
  }

  @Override
  public Map<String, Double> generateFeatures(LocalContext<String> item) {
    Map<String, Double> weights = Maps.newHashMap();
    for (int i = 0; i < offsets.length; i++) {
      String word = item.getItem(offsets[i], endFunction);
      if (commonWords.contains(word)) {
        weights.put(formatFeature(word, offsets[i]), 1.0);
      }
    }
    // weights.put("bias", 1.0);
    return weights;
  }

  private static String formatFeature(String word, int offset) {
    String featureName = ("WORD_" + offset + "_" + word).intern();
    featureName = featureName.intern();
    return featureName;
  }
}
