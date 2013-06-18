package com.jayantkrish.jklol.sequence;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.cli.TrainedModelSet;
import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.inference.MarginalSet;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Sequence tagger based on a graphical model. This class wraps a
 * graphical model constructed using
 * {@link TaggerUtils#buildFeaturizedSequenceModel} and trained using
 * any method.
 * 
 * @author jayantk
 * @param <I>
 * @param <O>
 */
public class FactorGraphSequenceTagger<I, O> extends TrainedModelSet implements SequenceTagger<I, O> {
  private static final long serialVersionUID = 1L;

  private final FeatureVectorGenerator<LocalContext<I>> featureGenerator;
  private final Class<O> outputClass;

  public FactorGraphSequenceTagger(ParametricFactorGraph modelFamily,
      SufficientStatistics parameters, DynamicFactorGraph instantiatedModel,
      FeatureVectorGenerator<LocalContext<I>> featureGenerator, Class<O> outputClass) {
    super(modelFamily, parameters, instantiatedModel);
    this.featureGenerator = Preconditions.checkNotNull(featureGenerator);
    this.outputClass = outputClass;
  }

  @Override
  public FeatureVectorGenerator<LocalContext<I>> getFeatureGenerator() {
    return featureGenerator;
  }

  @Override
  public TaggedSequence<I, O> tag(List<I> items) {
    TaggedSequence<I, O> sequence = new ListTaggedSequence<I, O>(items, null);

    DynamicAssignment input = TaggerUtils.reformatTrainingData(sequence,
        getFeatureGenerator(), getModelFamily().getVariables()).getInput();

    DynamicFactorGraph dfg = getInstantiatedModel();
    FactorGraph fg = dfg.conditional(input);

    JunctionTree jt = new JunctionTree();
    Assignment output = jt.computeMaxMarginals(fg).getNthBestAssignment(0);

    DynamicAssignment prediction = dfg.getVariables()
        .toDynamicAssignment(output, fg.getAllVariables());
    List<O> labels = Lists.newArrayList();
    for (Assignment plateAssignment : prediction.getPlateFixedAssignments(TaggerUtils.PLATE_NAME)) {
      List<Object> values = plateAssignment.getValues();
      labels.add(outputClass.cast(values.get(1)));
    }

    return new ListTaggedSequence<I, O>(items, labels);
  }
  
  @Override
  public MultitaggedSequence<I, O> multitag(List<I> items, double tagThreshold) {
    TaggedSequence<I, O> sequence = new ListTaggedSequence<I, O>(items, null);

    DynamicAssignment input = TaggerUtils.reformatTrainingData(sequence,
        getFeatureGenerator(), getModelFamily().getVariables()).getInput();

    DynamicFactorGraph dfg = getInstantiatedModel();
    FactorGraph fg = dfg.conditional(input);

    JunctionTree jt = new JunctionTree();
    MarginalSet marginals = jt.computeMarginals(fg);

    DynamicAssignment prediction = dfg.getVariables()
        .toDynamicAssignment(output, fg.getAllVariables());
    List<O> labels = Lists.newArrayList();
    for (Assignment plateAssignment : prediction.getPlateFixedAssignments(TaggerUtils.PLATE_NAME)) {
      List<Object> values = plateAssignment.getValues();
      labels.add(outputClass.cast(values.get(1)));
    }

    return new ListTaggedSequence<I, O>(items, labels);
  }
}
