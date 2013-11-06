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
public class WordAndPosFeatureGenerator implements FeatureGenerator<LocalContext<WordAndPos>, String> {
  private static final long serialVersionUID = 1L;

  private final int[] offsets;
  private final Set<String> commonWords;
  
  public static final Function<Integer, WordAndPos> END_FUNCTION = new Function<Integer, WordAndPos>() {
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

  public WordAndPosFeatureGenerator(int[] offsets, Set<String> commonWords) {
    this.offsets = ArrayUtils.copyOf(offsets, offsets.length);
    this.commonWords = ImmutableSet.copyOf(commonWords);
  }

  @Override
  public Map<String, Double> generateFeatures(LocalContext<WordAndPos> item) {
    Map<String, Double> weights = Maps.newHashMap();
    for (int i = 0; i < offsets.length; i++) {
      WordAndPos word = item.getItem(offsets[i], END_FUNCTION);
      if (commonWords.contains(word.getWord())) {
        String wordFeature = ("WORD_" + offsets[i] + "=" + word.getWord()).intern();
        weights.put(wordFeature, 1.0);

        String wordAndPosFeature = ("WORD+POS_" + offsets[i] + "=" + word.getWord() + "+" + word.getPos()).intern();
        weights.put(wordAndPosFeature, 1.0);
      }
    }

    String curWord = item.getItem(0, END_FUNCTION).getWord();
    if (commonWords.contains(curWord)) {
      for (int i = 0; i < offsets.length; i++) {
        WordAndPos word = item.getItem(offsets[i], END_FUNCTION);
        String wordAndPosFeature = ("WORD_0+POS_" + offsets[i] + "=" + curWord + "+" + word.getPos()).intern();
        weights.put(wordAndPosFeature, 1.0);
      }
    }

    return weights;
  }
}
