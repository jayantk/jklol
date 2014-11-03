package com.jayantkrish.jklol.ccg.supertag;

import java.util.List;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.preprocessing.FeatureGenerator;
import com.jayantkrish.jklol.sequence.LocalContext;
import com.jayantkrish.jklol.tensor.Tensor;

public class EmbeddingFeatureGenerator implements FeatureGenerator<LocalContext<WordAndPos>, String> {
  private static final long serialVersionUID = 1L;

  private final Map<String, Tensor> embeddings;
  private final String unknownWord;
  private final boolean usePos;

  private final List<String> featureNames;
  
  public EmbeddingFeatureGenerator(Map<String, Tensor> embeddings, String unknownWord, boolean usePos) {
    this.embeddings = Preconditions.checkNotNull(embeddings);
    this.unknownWord = Preconditions.checkNotNull(unknownWord);
    this.usePos = usePos;
    
    int dimensionality = Iterables.getFirst(embeddings.values(), null).getDimensionSizes()[0];
    this.featureNames = Lists.newArrayList();
    for (int i = 0; i < dimensionality; i++) {
      featureNames.add("embed-" + i);
    }
  }
  
  public List<String> getFeatureNames() {
    return featureNames;
  }

  @Override
  public Map<String, Double> generateFeatures(LocalContext<WordAndPos> item) {
    Map<String, Double> featureValues = Maps.newHashMap();
    
    String word = item.getItem().getWord();
    String pos = item.getItem().getPos();
    Tensor tensor = embeddings.get(word);
    if (tensor == null) {
      tensor = embeddings.get(unknownWord);
    }
    
    for (int i = 0; i < tensor.size(); i++) {
      int key = (int) tensor.indexToKeyNum(i);
      if (usePos) {
        featureValues.put((featureNames.get(key) + "_" + pos).intern(), tensor.getByIndex(i));
      } else {
        featureValues.put(featureNames.get(key), tensor.getByIndex(i));
      }
    }
    return featureValues;
  }
}
