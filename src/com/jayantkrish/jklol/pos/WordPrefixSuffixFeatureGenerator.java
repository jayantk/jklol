package com.jayantkrish.jklol.pos;

import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.preprocessing.FeatureGenerator;
import com.jayantkrish.jklol.sequence.LocalContext;

/**
 * Generates features from prefixes and suffixes of each word.
 *  
 * @author jayantk
 */
public class WordPrefixSuffixFeatureGenerator implements FeatureGenerator<LocalContext<String>, String> {

  private static final long serialVersionUID = 1L;

  private final int minPrefixLength;
  private final int maxPrefixLength;
  private final int minSuffixLength;
  private final int maxSuffixLength;
  
  private final Set<String> commonWords;
  
  public WordPrefixSuffixFeatureGenerator(int minPrefixLength, int maxPrefixLength,
      int minSuffixLength, int maxSuffixLength, Set<String> commonWords) {
    Preconditions.checkArgument(minPrefixLength >= 1);
    Preconditions.checkArgument(maxPrefixLength >= minPrefixLength);
    Preconditions.checkArgument(minSuffixLength >= 1);
    Preconditions.checkArgument(maxSuffixLength >= minSuffixLength);
    
    this.minPrefixLength = minPrefixLength;
    this.maxPrefixLength = maxPrefixLength;
    this.minSuffixLength = minSuffixLength;
    this.maxSuffixLength = maxSuffixLength;

    this.commonWords = ImmutableSet.copyOf(commonWords);
  }

  @Override
  public Map<String, Double> generateFeatures(LocalContext<String> item) {
    Map<String, Double> weights = Maps.newHashMap();
    String word = item.getItem();
    if (!commonWords.contains(word)) {
      generatePrefixSuffixFeatures(word, weights);
      
      if (word.matches(".*\\d.*")) {
        weights.put("HAS_DIGIT", 1.0);
      }
      if (word.matches(".*-.*")) {
        weights.put("HAS_HYPHEN", 1.0);
      }
      if (word.matches(".*[A-Z].*")) {
        weights.put("HAS_CAPITAL", 1.0);
      }
    }
    return weights;
  }

  private void generatePrefixSuffixFeatures(String word, Map<String, Double> weights) {
    if (word.length() == 1) {
      return;
    }

    int maxPrefixIndex = Math.min(word.length(), (maxPrefixLength - 1));
    for (int i = minPrefixLength; i <= maxPrefixIndex; i++) {
      String featureName = "PREFIX=" + word.substring(0, i).intern();
      weights.put(featureName, 1.0);
    }

    int len = word.length();
    int minSuffixIndex = Math.max(0, len - (maxSuffixLength - 1));
    for (int i = len - minSuffixLength; i >= minSuffixIndex; i--) {
      String featureName = "SUFFIX=" + word.substring(i, len).intern();
      weights.put(featureName, 1.0);
    }
  }
}