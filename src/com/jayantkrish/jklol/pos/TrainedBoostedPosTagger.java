package com.jayantkrish.jklol.pos;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.boost.ParametricFactorGraphEnsemble;
import com.jayantkrish.jklol.boost.SufficientStatisticsEnsemble;
import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.sequence.LocalContext;
import com.jayantkrish.jklol.sequence.TaggerUtils;
import com.jayantkrish.jklol.util.Assignment;


public class TrainedBoostedPosTagger implements PosTagger, Serializable {
  private static final long serialVersionUID = 1L;

  private final ParametricFactorGraphEnsemble parametricFamily;
  private final SufficientStatisticsEnsemble parameters;
  private final DynamicFactorGraph factorGraph;
  
  private final FeatureVectorGenerator<LocalContext<String>> featureGenerator;
  
  public TrainedBoostedPosTagger(ParametricFactorGraphEnsemble parametricFamily, 
      SufficientStatisticsEnsemble parameters, DynamicFactorGraph factorGraph,
      FeatureVectorGenerator<LocalContext<String>> featureGenerator) {
    this.parametricFamily = Preconditions.checkNotNull(parametricFamily);
    this.parameters = Preconditions.checkNotNull(parameters);
    this.factorGraph = Preconditions.checkNotNull(factorGraph);
    
    this.featureGenerator = Preconditions.checkNotNull(featureGenerator);
  }
  
  public SufficientStatisticsEnsemble getParameters() {
    return parameters;
  }

  @Override
  public FeatureVectorGenerator<LocalContext<String>> getFeatureGenerator() {
    return featureGenerator;
  }

  @Override
  public PosTaggedSentence tag(List<String> words) {
    List<String> posTags = Collections.<String>nCopies(words.size(), "");
    PosTaggedSentence sent = new PosTaggedSentence(words, posTags);

    DynamicAssignment input = TaggerUtils.reformatTrainingData(sent, 
        getFeatureGenerator(), parametricFamily.getVariables()).getInput();

    FactorGraph fg = factorGraph.conditional(input);
    JunctionTree jt = new JunctionTree();
    Assignment output = jt.computeMaxMarginals(fg).getNthBestAssignment(0);

    DynamicAssignment prediction = factorGraph.getVariables()
        .toDynamicAssignment(output, fg.getAllVariables());
    List<String> labels = Lists.newArrayList();
    for (Assignment plateAssignment : prediction.getPlateFixedAssignments(TaggerUtils.PLATE_NAME)) {
      List<Object> values = plateAssignment.getValues();
      labels.add((String) values.get(1));
    }

    return new PosTaggedSentence(words, labels);
  }

}
