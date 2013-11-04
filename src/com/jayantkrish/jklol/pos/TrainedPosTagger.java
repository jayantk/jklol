package com.jayantkrish.jklol.pos;

import java.util.List;

import com.google.common.base.Function;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.sequence.FactorGraphSequenceTagger;
import com.jayantkrish.jklol.sequence.LocalContext;
import com.jayantkrish.jklol.sequence.TaggedSequence;

public class TrainedPosTagger extends FactorGraphSequenceTagger<String, String> implements PosTagger {
  private static final long serialVersionUID = 1L;

  public TrainedPosTagger(ParametricFactorGraph modelFamily,
      SufficientStatistics parameters, DynamicFactorGraph instantiatedModel,
      FeatureVectorGenerator<LocalContext<String>> featureGenerator,
      Function<? super LocalContext<String>, ? extends Object> inputGenerator) {
    super(modelFamily, parameters, instantiatedModel, featureGenerator, inputGenerator,
        String.class, null, null);
  }

  @Override
  public PosTaggedSentence tag(List<String> words) {
    TaggedSequence<String, String> sequence = super.tag(words);
    return new PosTaggedSentence(sequence.getItems(), sequence.getLabels());
 }
}