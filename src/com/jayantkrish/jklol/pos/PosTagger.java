package com.jayantkrish.jklol.pos;

import java.util.List;

import com.jayantkrish.jklol.pos.PosTaggedSentence.LocalContext;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;

public interface PosTagger {

  FeatureVectorGenerator<LocalContext> getFeatureGenerator();
  
  PosTaggedSentence tagWords(List<String> words);
}
