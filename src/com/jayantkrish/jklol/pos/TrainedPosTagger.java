package com.jayantkrish.jklol.pos;

import java.util.Collections;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.cli.TrainedModelSet;
import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.pos.PosTaggedSentence.LocalContext;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.util.Assignment;

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

  public PosTaggedSentence tagWords(List<String> words) {
    List<String> posTags = Collections.<String>nCopies(words.size(), "");
    PosTaggedSentence sent = new PosTaggedSentence(words, posTags);

    DynamicAssignment input = PosTaggerUtils.reformatTrainingData(sent, 
        getFeatureGenerator(), getModelFamily()).getInput();

    DynamicFactorGraph dfg = getInstantiatedModel();
    FactorGraph fg = dfg.conditional(input);

    JunctionTree jt = new JunctionTree();
    Assignment output = jt.computeMaxMarginals(fg).getNthBestAssignment(0);

    DynamicAssignment prediction = dfg.getVariables()
        .toDynamicAssignment(output, fg.getAllVariables());
    List<String> labels = Lists.newArrayList();
    for (Assignment plateAssignment : prediction.getPlateFixedAssignments(PosTaggerUtils.PLATE_NAME)) {
      List<Object> values = plateAssignment.getValues();
      labels.add((String) values.get(1));
    }

    return new PosTaggedSentence(words, posTags);
  }
}