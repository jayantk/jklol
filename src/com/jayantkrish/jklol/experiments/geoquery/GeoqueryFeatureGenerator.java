package com.jayantkrish.jklol.experiments.geoquery;

import java.util.List;
import java.util.Map;

import com.google.common.collect.Maps;
import com.jayantkrish.jklol.ccg.lexicon.StringContext;
import com.jayantkrish.jklol.preprocessing.FeatureGenerator;

public class GeoqueryFeatureGenerator implements FeatureGenerator<StringContext, String> {
  private static final long serialVersionUID = 1L;
  
  private static final int[] OFFSETS = {-1, 1};
  
  @Override
  public Map<String, Double> generateFeatures(StringContext item) {
    Map<String, Double> features = Maps.newHashMap();

    for (int offset : OFFSETS) {
      int word0Index = offset < 0 ? item.getSpanStart() : item.getSpanEnd();
      String word0 = item.getWords().get(word0Index);

      int spanIndex = offset < 0 ? item.getSpanStart() : item.getSpanEnd();
      String word = getWord(spanIndex + offset, item.getWords());
      String featureName = formatFeature(word, offset, word0);
      features.put(featureName, 1.0);
    }

    return features;
  }
  
  private static String getWord(int index, List<String> words) {
    if (index < 0) {
      String startWord = "<START_" + index + ">";
      startWord.intern();
      return startWord;
    } else if (index >= words.size()) {
      String endWord = "<END_" + (index - words.size()) + ">";
      endWord.intern();
      return endWord;
    } else {
      return words.get(index);
    }
  }
  
  private static String formatFeature(String word, int offset, String word0) {
    String featureName = ("WORD_" + offset + "_" + word + "&WORD_0=" + word0).intern();
    featureName = featureName.intern();
    return featureName;
  }
}
