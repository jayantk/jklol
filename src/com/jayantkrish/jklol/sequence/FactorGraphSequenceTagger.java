package com.jayantkrish.jklol.sequence;

import java.util.List;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.cli.TrainedModelSet;
import com.jayantkrish.jklol.inference.FactorMarginalSet;
import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.dynamic.DynamicVariableSet;
import com.jayantkrish.jklol.models.dynamic.VariablePattern.VariableMatch;
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
  private static final long serialVersionUID = 2L;

  private final FeatureVectorGenerator<LocalContext<I>> featureGenerator;
  private final Function<? super LocalContext<I>, ? extends Object> inputGen;
  private final Class<O> outputClass;
  
  // If provided, the tagger automatically adds special start
  // symbols to each sequence.
  private final I startInput;
  private final O startLabel;

  public FactorGraphSequenceTagger(ParametricFactorGraph modelFamily,
      SufficientStatistics parameters, DynamicFactorGraph instantiatedModel,
      FeatureVectorGenerator<LocalContext<I>> featureGenerator, 
      Function<? super LocalContext<I>, ? extends Object> inputGen, Class<O> outputClass,
      I startInput, O startLabel) {
    super(modelFamily, parameters, instantiatedModel);
    this.featureGenerator = Preconditions.checkNotNull(featureGenerator);
    this.inputGen = Preconditions.checkNotNull(inputGen);
    this.outputClass = outputClass;
    
    this.startInput = startInput;
    this.startLabel = startLabel;

    // Either both or neither are null. 
    Preconditions.checkArgument(!(startInput == null ^ startLabel == null));
  }

  @Override
  public FeatureVectorGenerator<LocalContext<I>> getFeatureGenerator() {
    return featureGenerator;
  }
  
  @Override
  public Function<? super LocalContext<I>, ? extends Object> getInputGenerator() {
    return inputGen;
  }
  
  public I getStartInput() {
    return startInput;
  }
  
  public O getStartLabel() {
    return startLabel;
  }

  @Override
  public TaggedSequence<I, O> tag(List<I> items) {
    TaggedSequence<I, O> sequence = new ListTaggedSequence<I, O>(items, null);

    DynamicAssignment input = TaggerUtils.reformatTrainingData(sequence, getFeatureGenerator(),
        inputGen, getModelFamily().getVariables(), startInput, startLabel).getInput();

    DynamicFactorGraph dfg = getInstantiatedModel();
    FactorGraph fg = dfg.conditional(input);

    JunctionTree jt = new JunctionTree(true);
    Assignment output = jt.computeMaxMarginals(fg).getNthBestAssignment(0);

    DynamicAssignment prediction = dfg.getVariables()
        .toDynamicAssignment(output, fg.getAllVariables());
    List<O> labels = Lists.newArrayList();
    List<Assignment> predictedAssignments = prediction.getPlateFixedAssignments(TaggerUtils.PLATE_NAME);
    int startIndex = (startInput == null) ? 0 : 1;
    for (int i = startIndex; i < predictedAssignments.size(); i++) {
      Assignment plateAssignment = predictedAssignments.get(i);
      List<Object> values = plateAssignment.getValues();
      labels.add(outputClass.cast(values.get(2)));
    }

    return new ListTaggedSequence<I, O>(items, labels);
  }
  
  @Override
  public MultitaggedSequence<I, O> multitag(List<I> items, double tagThreshold) {
    Preconditions.checkArgument(tagThreshold >= 0 && tagThreshold <= 1.0, "tagThreshold must be between 0 and 1");

    TaggedSequence<I, O> sequence = new ListTaggedSequence<I, O>(items, null);
    DynamicAssignment input = TaggerUtils.reformatTrainingData(sequence, getFeatureGenerator(),
        inputGen, getModelFamily().getVariables(), startInput, startLabel).getInput();

    DynamicFactorGraph dfg = getInstantiatedModel();
    DynamicVariableSet dynamicVariables = dfg.getVariables();
    FactorGraph fg = dfg.conditional(input);

    JunctionTree jt = new JunctionTree(true);
    FactorMarginalSet marginals = jt.computeMarginals(fg);

    List<VariableMatch> matches = dynamicVariables.getPlateInstantiations(
        marginals.getVariables(), TaggerUtils.PLATE_NAME);
    Preconditions.checkState((matches.size() == items.size() && startInput == null) 
        || (matches.size() == items.size() + 1 && startInput != null));

    VariableNumMap templateLabelVar = dynamicVariables.getPlate(TaggerUtils.PLATE_NAME)
        .getFixedVariables().getVariablesByName(TaggerUtils.OUTPUT_NAME);

    List<List<O>> labels = Lists.newArrayList();
    List<List<Double>> labelProbs = Lists.newArrayList();
    int startIndex = (startInput == null) ? 0 : 1;
    for (int i = startIndex; i < matches.size(); i++) {
      VariableMatch match = matches.get(i);
      int varNum = match.getMatchedVariablesFromTemplateVariables(templateLabelVar).getOnlyVariableNum();
      DiscreteFactor marginal = marginals.getUnnormalizedMarginal(varNum).coerceToDiscrete();
      List<Assignment> bestAssignments = marginal.getMostLikelyAssignments(-1);

      List<O> curLabels = Lists.newArrayList();
      List<Double> curProbs = Lists.newArrayList();
      double bestProb = -1;
      for (Assignment assignment : bestAssignments) {
        double curProb = marginal.getUnnormalizedProbability(assignment);
        System.out.println(assignment + " " + curProb);
        if (bestProb == -1) {
          bestProb = curProb;
          curLabels.add(outputClass.cast(assignment.getValue(varNum)));
          curProbs.add(curProb);
        } else if (curProb > tagThreshold * bestProb) {
          curLabels.add(outputClass.cast(assignment.getValue(varNum)));
          curProbs.add(curProb);
        }
      }

      labels.add(curLabels);
      labelProbs.add(curProbs);
    }

    return new ListMultitaggedSequence<I, O>(items, labels, labelProbs);
  }
}
