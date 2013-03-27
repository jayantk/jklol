package com.jayantkrish.jklol.preprocessing;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import com.jayantkrish.jklol.util.CountAccumulator;

/**
 * Unit tests for {@link FeatureGenerators}.
 * 
 * @author jayantk
 */
public class FeatureGeneratorsTest extends TestCase {

  private FeatureGenerator<String, String> wordCount;
  private FeatureGenerator<String, String> firstLetters;
  
  public void setUp() {
    wordCount = new FeatureGenerator<String, String>() {
      private static final long serialVersionUID = 1L;
      @Override
      public Map<String, Double> generateFeatures(String item) {
        CountAccumulator<String> counts = CountAccumulator.create();
        for (String word : Arrays.asList(item.split(" "))) {
          counts.increment(word, 2.0);
        }
        return counts.getCountMap();
      }
    };
    
    firstLetters = new FeatureGenerator<String, String>() {
      private static final long serialVersionUID = 1L;

      @Override
      public Map<String, Double> generateFeatures(String item) {
        CountAccumulator<String> counts = CountAccumulator.create();
        for (String word : Arrays.asList(item.split(" "))) {
          counts.increment(word.substring(0, 1), 1.0);
        }
        return counts.getCountMap();
      }
    };
  }
  
  public void testCombinedFeatureGenerator() {
    @SuppressWarnings("unchecked")
    FeatureGenerator<String, String> combined = FeatureGenerators
        .combinedFeatureGenerator(firstLetters, wordCount);
    
    Map<String, Double> features = combined.generateFeatures("a cat car");
    assertEquals(4, features.size());
    assertEquals(3.0, features.get("a"));
    assertEquals(2.0, features.get("c"));
    assertEquals(2.0, features.get("cat"));
    assertEquals(2.0, features.get("car"));
  }

  public void testProductFeatureGenerator() {
    @SuppressWarnings("unchecked")
    FeatureGenerator<String, List<String>> combined = FeatureGenerators
    .productFeatureGenerator(firstLetters, wordCount);

    Map<List<String>, Double> features = combined.generateFeatures("a cat car");
    assertEquals(6, features.size());
    assertEquals(2.0, features.get(Arrays.asList("a", "a")));
    assertEquals(2.0, features.get(Arrays.asList("a", "cat")));
    assertEquals(4.0, features.get(Arrays.asList("c", "cat")));
  }
}
