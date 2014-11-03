package com.jayantkrish.jklol.pos;

import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.preprocessing.DictionaryFeatureVectorGenerator;
import com.jayantkrish.jklol.preprocessing.FeatureGenerator;
import com.jayantkrish.jklol.preprocessing.FeatureGenerators;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.sequence.LocalContext;
import com.jayantkrish.jklol.sequence.TaggerUtils;
import com.jayantkrish.jklol.util.CountAccumulator;
import com.jayantkrish.jklol.util.IndexedList;
import com.jayantkrish.jklol.util.IoUtils;

/**
 * Utilities for manipulating part-of-speech tagged data 
 * and building part of speech taggers.
 * 
 * @author jayant
 */
public class PosTaggerUtils {

  public static List<PosTaggedSentence> readTrainingData(String trainingFilename) {
    List<PosTaggedSentence> sentences = Lists.newArrayList();
    for (String line : IoUtils.readLines(trainingFilename)) {
      sentences.add(PosTaggedSentence.parseFrom(line));
    }
    return sentences;
  }

  public static FeatureVectorGenerator<LocalContext<String>> buildFeatureVectorGenerator(
      List<PosTaggedSentence> sentences, int commonWordThreshold, boolean noUnknownWordFeatures) {
    List<LocalContext<String>> contexts = TaggerUtils.extractContextsFromData(sentences);
    CountAccumulator<String> wordCounts = CountAccumulator.create();
    for (LocalContext<String> context : contexts) {
      wordCounts.increment(context.getItem(), 1.0);
    }    
    Set<String> commonWords = Sets.newHashSet(wordCounts.getKeysAboveCountThreshold(
        commonWordThreshold));

    if (noUnknownWordFeatures) {
      WordContextFeatureGenerator wordGen = new WordContextFeatureGenerator(new int[] {-1, 0, 1},
          commonWords);
      return DictionaryFeatureVectorGenerator.createFromData(contexts, wordGen, true);
    } else {
      WordContextFeatureGenerator wordGen = new WordContextFeatureGenerator(new int[] {-1, 0, 1},
          commonWords);
      WordPrefixSuffixFeatureGenerator prefixGen = new WordPrefixSuffixFeatureGenerator(1, 5, 1, 5, commonWords);
      @SuppressWarnings("unchecked")
      FeatureGenerator<LocalContext<String>, String> featureGen = FeatureGenerators
          .combinedFeatureGenerator(wordGen, prefixGen);

      // Count threshold the generated features to eliminate rare features.
      CountAccumulator<String> wordFeatureCounts = FeatureGenerators.getFeatureCounts(wordGen, contexts);
      CountAccumulator<String> prefixFeatureCounts = FeatureGenerators.getFeatureCounts(prefixGen, contexts);
      IndexedList<String> featureDictionary = IndexedList.create();
      featureDictionary.addAll(wordFeatureCounts.getKeysAboveCountThreshold(commonWordThreshold));
      featureDictionary.addAll(prefixFeatureCounts.getKeysAboveCountThreshold(35.0));

      return new DictionaryFeatureVectorGenerator<LocalContext<String>, String>(featureDictionary, featureGen, true);
    }
  }
}
