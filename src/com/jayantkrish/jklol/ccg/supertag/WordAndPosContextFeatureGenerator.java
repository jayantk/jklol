package com.jayantkrish.jklol.ccg.supertag;

import java.util.Map;
import java.util.Set;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.preprocessing.FeatureGenerator;
import com.jayantkrish.jklol.sequence.LocalContext;
import com.jayantkrish.jklol.util.ArrayUtils;

/**
 * Generates indicator features for the words and pos tags in the
 * region of the sequence surrounding the current word index.
 *  
 * @author jayantk
 */
public class WordAndPosContextFeatureGenerator implements FeatureGenerator<LocalContext<WordAndPos>, String> {
  private static final long serialVersionUID = 1L;

  private final int[] offsets;
  private final Set<String> commonWords;
  
  private static final Function<Integer, WordAndPos> endFunction = new Function<Integer, WordAndPos>() {
    @Override
    public WordAndPos apply(Integer index) {
      if (index <= 0) {
        String value = ("<START_" + index + ">").intern();
        return new WordAndPos(value, value);
      } else {
        String value = ("<END_" + index + ">").intern();
        return new WordAndPos(value, value);
      }
    }
  };

  public WordAndPosContextFeatureGenerator(int[] offsets, Set<String> commonWords) {
    this.offsets = ArrayUtils.copyOf(offsets, offsets.length);
    this.commonWords = ImmutableSet.copyOf(commonWords);
  }

  @Override
  public Map<String, Double> generateFeatures(LocalContext<WordAndPos> item) {
    Map<String, Double> weights = Maps.newHashMap();
    for (int i = 0; i < offsets.length; i++) {
      WordAndPos word = item.getItem(offsets[i], endFunction);
      if (commonWords.contains(word.getWord())) {
        weights.put(formatFeature(word.getWord(), offsets[i]), 1.0);
      }
      weights.put(formatPosFeature(word.getPos(), offsets[i]), 1.0);
    }
    
    for (int i = 0; i < 2; i++) {
      WordAndPos prevWord = item.getItem(i - 1, endFunction);
      WordAndPos curWord = item.getItem(i, endFunction);
      
      String featureName = ("POS_" + (i - 1) + "_" + i + "_" + prevWord +"_" + curWord).intern();
      weights.put(featureName, 1.0);
    }
    return weights;
  }

  private static String formatFeature(String word, int offset) {
    String featureName = ("WORD_" + offset + "=" + word).intern();
    featureName = featureName.intern();
    return featureName;
  }
  
  private static String formatPosFeature(String pos, int offset) {
    String featureName = ("POS_" + offset + "=" + pos).intern();
    featureName = featureName.intern();
    return featureName;
  }
}
