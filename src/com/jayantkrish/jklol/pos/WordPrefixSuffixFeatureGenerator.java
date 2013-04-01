package com.jayantkrish.jklol.pos;

import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.pos.PosTaggedSentence.LocalContext;
import com.jayantkrish.jklol.preprocessing.FeatureGenerator;

/**
 * Generates features from prefixes and suffixes of each word.
 *  
 * @author jayantk
 */
public class WordPrefixSuffixFeatureGenerator implements FeatureGenerator<LocalContext, String> {

  private static final long serialVersionUID = 1L;
  
  private final int maxPrefixLength;
  private final int maxSuffixLength;
  
  private final Set<String> commonWords;
  
  public WordPrefixSuffixFeatureGenerator(int maxPrefixLength, int maxSuffixLength,
      Set<String> commonWords) {
    Preconditions.checkArgument(maxPrefixLength >= 0);
    Preconditions.checkArgument(maxSuffixLength >= 0);
    
    this.maxPrefixLength = maxPrefixLength;
    this.maxSuffixLength = maxSuffixLength;
    
    this.commonWords = ImmutableSet.copyOf(commonWords);
  }

  @Override
  public Map<String, Double> generateFeatures(LocalContext item) {
    Map<String, Double> weights = Maps.newHashMap();
    String word = item.getWord();
    if (!commonWords.contains(word)) {
      generatePrefixSuffixFeatures(word, weights);
      
      if (word.matches("\\d")) {
        weights.put("HAS_DIGIT", 1.0);
      }
      if (word.matches("-")) {
        weights.put("HAS_HYPHEN", 1.0);
      }
      if (word.matches("[A-Z]")) {
        weights.put("HAS_CAPITAL", 1.0);
      }
    }
    return weights;
  }

  private void generatePrefixSuffixFeatures(String word, Map<String, Double> weights) {
    if (word.length() == 1) {
      return;
    }

    int maxPrefixIndex = Math.min(word.length(), maxPrefixLength);
    for (int i = 1; i <= maxPrefixIndex; i++) {
      String featureName = "PREFIX=" + word.substring(0, i).intern();
      weights.put(featureName, 1.0);
    }

    int len = word.length();
    int minSuffixIndex = Math.max(0, len - maxSuffixLength);
    for (int i = len - 1; i >= minSuffixIndex; i--) {
      String featureName = "SUFFIX=" + word.substring(i, len).intern();
      weights.put(featureName, 1.0);
    }
  }
}
