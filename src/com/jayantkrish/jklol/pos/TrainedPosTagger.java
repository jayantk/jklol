package com.jayantkrish.jklol.pos;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.cli.TrainedModelSet;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.pos.PosTaggedSentence.LocalContext;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;

public class TrainedPosTagger extends TrainedModelSet {
  private static final long serialVersionUID = 1L;

  private final FeatureVectorGenerator<LocalContext> featureGenerator;

  public TrainedPosTagger(ParametricFactorGraph modelFamily,
      SufficientStatistics parameters, DynamicFactorGraph instantiatedModel,
      FeatureVectorGenerator<LocalContext> featureGenerator) {
    super(modelFamily, parameters, instantiatedModel);
    this.featureGenerator = Preconditions.checkNotNull(featureGenerator);
  }

  public FeatureVectorGenerator<LocalContext> getFeatureGenerator() {
    return featureGenerator;
  }
}