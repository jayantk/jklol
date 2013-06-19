package com.jayantkrish.jklol.sequence;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.evaluation.Example;
import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.ObjectVariable;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.dynamic.DynamicVariableSet;
import com.jayantkrish.jklol.models.dynamic.VariableNumPattern;
import com.jayantkrish.jklol.models.loglinear.ConditionalLogLinearFactor;
import com.jayantkrish.jklol.models.loglinear.IndicatorLogLinearFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraphBuilder;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.parallel.MapReduceConfiguration;
import com.jayantkrish.jklol.parallel.MapReduceExecutor;
import com.jayantkrish.jklol.parallel.Mapper;
import com.jayantkrish.jklol.parallel.Reducer.SimpleReducer;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.training.GradientOptimizer;
import com.jayantkrish.jklol.training.GradientOracle;
import com.jayantkrish.jklol.training.LoglikelihoodOracle;
import com.jayantkrish.jklol.training.MaxMarginOracle;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Utilities for defining, training, and evaluating sequence taggers.
 * 
 * @author jayantk
 */
public class TaggerUtils {

  public static final String PLATE_NAME = "plate";
  public static final String INPUT_NAME = "inputFeatures";
  public static final String OUTPUT_NAME = "label";

  public static final String WORD_LABEL_FACTOR = "wordLabelFactor";
  public static final String TRANSITION_FACTOR = "transition";

  public static <I, O> List<LocalContext<I>> extractContextsFromData(
      List<? extends TaggedSequence<I, O>> sequences) {
    List<LocalContext<I>> contexts = Lists.newArrayList();
    for (TaggedSequence<I, O> sequence : sequences) {
      contexts.addAll(sequence.getLocalContexts());
    }
    return contexts;
  }

  public static <I, O> Example<DynamicAssignment, DynamicAssignment> reformatTrainingData(
      TaggedSequence<I, O> sequence, FeatureVectorGenerator<LocalContext<I>> featureGen,
      DynamicVariableSet modelVariables) {
    List<TaggedSequence<I, O>> sequences = Lists.newArrayList();
    sequences.add(sequence);
    List<Example<DynamicAssignment, DynamicAssignment>> examples = reformatTrainingData(
        sequences, featureGen, modelVariables);
    return examples.get(0);
  }

  /**
   * Converts training data as sequences into assignments that can be
   * used for parameter estimation.
   * 
   * @param sequences
   * @param featureGen
   * @param model
   * @return
   */
  public static <I, O> List<Example<DynamicAssignment, DynamicAssignment>> reformatTrainingData(
      List<? extends TaggedSequence<I, O>> sequences, FeatureVectorGenerator<LocalContext<I>> featureGen,
      DynamicVariableSet modelVariables) {
    DynamicVariableSet plate = modelVariables.getPlate(PLATE_NAME);
    VariableNumMap x = plate.getFixedVariables().getVariablesByName(INPUT_NAME);
    VariableNumMap y = plate.getFixedVariables().getVariablesByName(OUTPUT_NAME);

    List<Example<DynamicAssignment, DynamicAssignment>> examples = Lists.newArrayList();
    for (TaggedSequence<I, O> sequence : sequences) {
      List<Assignment> inputs = Lists.newArrayList();
      List<LocalContext<I>> contexts = sequence.getLocalContexts();
      for (int i = 0; i < contexts.size(); i++) {
        inputs.add(x.outcomeArrayToAssignment(featureGen.apply(contexts.get(i))));
      }
      DynamicAssignment input = DynamicAssignment.createPlateAssignment(PLATE_NAME, inputs);

      DynamicAssignment output = DynamicAssignment.EMPTY;
      if (sequence.getLabels() != null) {
        List<Assignment> outputs = Lists.newArrayList();
        List<O> labels = sequence.getLabels();
        for (int i = 0; i < contexts.size(); i++) {
          outputs.add(y.outcomeArrayToAssignment(labels.get(i)));
        }
        output = DynamicAssignment.createPlateAssignment(PLATE_NAME, outputs);
      }

      examples.add(Example.create(input, output));
    }

    return examples;
  }

  /**
   * Constructs a graphical model that can be trained to perform
   * sequence tagging. The model expects as input a sequence of
   * feature vectors, with the feature set given by
   * {@code featureDictionary}, and outputs a sequence of labels drawn
   * from {@code labelSet}. The model is a standard sequence model,
   * with local input/label factors and transition factors between
   * adjacent labels (unless {@code noTransitions} is true).
   * 
   * @param labelSet
   * @param featureDictionary
   * @param noTransitions
   * @return
   */
  public static <O> ParametricFactorGraph buildFeaturizedSequenceModel(Set<O> labelSet,
      DiscreteVariable featureDictionary, boolean noTransitions) {
    DiscreteVariable labelType = new DiscreteVariable("labels", labelSet);
    ObjectVariable inputVectorType = new ObjectVariable(Tensor.class);

    // Create a dynamic factor graph with a single plate replicating
    // the input/output variables.
    ParametricFactorGraphBuilder builder = new ParametricFactorGraphBuilder();
    builder.addPlate(TaggerUtils.PLATE_NAME, new VariableNumMap(Ints.asList(1, 2),
        Arrays.asList(TaggerUtils.INPUT_NAME, TaggerUtils.OUTPUT_NAME),
        Arrays.<Variable> asList(inputVectorType, labelType)), 10000);
    String inputPattern = TaggerUtils.PLATE_NAME + "/?(0)/" + TaggerUtils.INPUT_NAME;
    String outputPattern = TaggerUtils.PLATE_NAME + "/?(0)/" + TaggerUtils.OUTPUT_NAME;
    String nextOutputPattern = TaggerUtils.PLATE_NAME + "/?(1)/" + TaggerUtils.OUTPUT_NAME;

    // Add a classifier from local word contexts to pos tags.
    VariableNumMap plateVars = new VariableNumMap(Ints.asList(1, 2),
        Arrays.asList(inputPattern, outputPattern), Arrays.<Variable> asList(inputVectorType, labelType));
    VariableNumMap wordVectorVar = plateVars.getVariablesByName(inputPattern);
    VariableNumMap posVar = plateVars.getVariablesByName(outputPattern);
    ConditionalLogLinearFactor wordClassifier = new ConditionalLogLinearFactor(wordVectorVar, posVar,
        VariableNumMap.emptyMap(), featureDictionary);
    builder.addFactor(TaggerUtils.WORD_LABEL_FACTOR, wordClassifier,
        VariableNumPattern.fromTemplateVariables(plateVars, VariableNumMap.emptyMap(), builder.getDynamicVariableSet()));

    // Add a factor connecting adjacent labels.
    if (!noTransitions) {
      VariableNumMap adjacentVars = new VariableNumMap(Ints.asList(0, 1),
          Arrays.asList(outputPattern, nextOutputPattern), Arrays.asList(labelType, labelType));
      builder.addFactor(TaggerUtils.TRANSITION_FACTOR, IndicatorLogLinearFactor.createDenseFactor(adjacentVars),
          VariableNumPattern.fromTemplateVariables(adjacentVars, VariableNumMap.emptyMap(), builder.getDynamicVariableSet()));
    }

    return builder.build();
  }

  /**
   * Trains a sequence model.
   * 
   * @param sequenceModelFamily
   * @param trainingData
   * @param outputClass
   * @param featureGen
   * @param optimizer
   * @param useMaxMargin
   * @return
   */
  public static <I, O> FactorGraphSequenceTagger<I, O> trainSequenceModel(
      ParametricFactorGraph sequenceModelFamily, List<? extends TaggedSequence<I, O>> trainingData,
      Class<O> outputClass, FeatureVectorGenerator<LocalContext<I>> featureGen,
      GradientOptimizer optimizer, boolean useMaxMargin) {

    // Estimate parameters.
    List<Example<DynamicAssignment, DynamicAssignment>> examples = TaggerUtils
        .reformatTrainingData(trainingData, featureGen, sequenceModelFamily.getVariables());
    SufficientStatistics parameters = estimateParameters(sequenceModelFamily, examples,
        optimizer, useMaxMargin);

    // Save model to disk.
    DynamicFactorGraph factorGraph = sequenceModelFamily.getModelFromParameters(parameters);
    return new FactorGraphSequenceTagger<I, O>(sequenceModelFamily, parameters,
        factorGraph, featureGen, outputClass);
  }

  private static SufficientStatistics estimateParameters(ParametricFactorGraph sequenceModel,
      List<Example<DynamicAssignment, DynamicAssignment>> trainingData,
      GradientOptimizer optimizer, boolean useMaxMargin) {
    System.out.println(trainingData.size() + " training examples.");

    // Estimate parameters
    GradientOracle<DynamicFactorGraph, Example<DynamicAssignment, DynamicAssignment>> oracle;
    SufficientStatistics initialParameters = sequenceModel.getNewSufficientStatistics();
    initialParameters.makeDense();
    System.out.println("Training...");
    if (useMaxMargin) {
      oracle = new MaxMarginOracle(sequenceModel, new MaxMarginOracle.HammingCost(), new JunctionTree());
    } else {
      oracle = new LoglikelihoodOracle(sequenceModel, new JunctionTree());
    }

    SufficientStatistics parameters = optimizer.train(oracle, initialParameters, trainingData);
    return parameters;
  }

  /**
   * Evaluates the performance of {@code tagger} on
   * {@code evaluationData}, returning both per-item and per-sequence
   * error rates.
   * 
   * @param tagger
   * @param evaluationData
   * @return
   */
  public static <I, O> SequenceTaggerError evaluateTagger(SequenceTagger<I, O> tagger,
      Collection<? extends TaggedSequence<I, O>> evaluationData) {
    MapReduceExecutor executor = MapReduceConfiguration.getMapReduceExecutor();
    return executor.mapReduce(evaluationData, new SequenceTaggerEvaluationMapper<I, O>(tagger, -1),
        new SequenceTaggerEvaluationReducer());
  }

  /**
   * Evaluates the performance of {@code tagger} as a multi-tagger on
   * {@code evaluationData}, returning both per-item and per-sequence
   * error rates. For this evaluation, the label for an item is
   * considered correct if the tagger produces the correct label in
   * its set of predicted labels for the item.
   * 
   * @param tagger
   * @param evaluationData
   * @param multitagThreshold
   * @return
   */
  public static <I, O> SequenceTaggerError evaluateMultitagger(SequenceTagger<I, O> tagger,
      Collection<? extends TaggedSequence<I, O>> evaluationData, double multitagThreshold) {
    MapReduceExecutor executor = MapReduceConfiguration.getMapReduceExecutor();
    return executor.mapReduce(evaluationData,
        new SequenceTaggerEvaluationMapper<I, O>(tagger, multitagThreshold),
        new SequenceTaggerEvaluationReducer());
  }

  private TaggerUtils() {
    // Prevent instantiation.
  }

  public static class SequenceTaggerError {
    // Total number of items across all sequences, and the
    // number of those items for which the correct label 
    // was predicted.
    private int numItemsCorrect;
    private int numItems;
    
    // Total number of predicted labels across all items.
    // This only differs from numItems if evaluating a
    // multitagger.
    private int numLabels;

    private int numSentencesCorrect;
    private int numSentences;

    public SequenceTaggerError(int numItemsCorrect, int numItems, int numLabels,
        int numSentencesCorrect, int numSentences) {
      this.numItemsCorrect = numItemsCorrect;
      this.numItems = numItems;
      this.numLabels = numLabels;
      this.numSentencesCorrect = numSentencesCorrect;
      this.numSentences = numSentences;
    }

    public static SequenceTaggerError zero() {
      return new SequenceTaggerError(0, 0, 0, 0, 0);
    }

    public int getNumTagsCorrect() {
      return numItemsCorrect;
    }

    public int getNumTags() {
      return numItems;
    }

    public double getTagAccuracy() {
      return ((double) numItemsCorrect) / numItems;
    }
    
    public double getTagsPerItem() {
      return ((double) numLabels) / numItems;
    }

    public int getNumSentencesCorrect() {
      return numSentencesCorrect;
    }

    public int getNumSentences() {
      return numSentences;
    }

    public double getSentenceAccuracy() {
      return ((double) numSentencesCorrect) / numSentences;
    }

    public void increment(SequenceTaggerError other) {
      this.numItemsCorrect += other.numItemsCorrect;
      this.numItems += other.numItems;
      this.numLabels += other.numLabels;
      this.numSentencesCorrect += other.numSentencesCorrect;
      this.numSentences += other.numSentences;
    }
  }

  private static class SequenceTaggerEvaluationMapper<I, O> extends Mapper<TaggedSequence<I, O>, SequenceTaggerError> {
    private final SequenceTagger<I, O> tagger;
    private final double multitagThreshold;

    public SequenceTaggerEvaluationMapper(SequenceTagger<I, O> tagger, double multitagThreshold) {
      this.tagger = Preconditions.checkNotNull(tagger);
      this.multitagThreshold = multitagThreshold;
    }

    @Override
    public SequenceTaggerError map(TaggedSequence<I, O> item) {
      // Run the tagger to get either the best prediction or the
      // set of predicted labels for each element of the input
      // sequence.
      List<List<O>> prediction = null;
      if (multitagThreshold >= 0.0) {
        prediction = tagger.multitag(item.getItems(), multitagThreshold).getLabels();
      } else {
        List<O> predictionList = tagger.tag(item.getItems()).getLabels();
        prediction = Lists.newArrayList();
        for (O predicted : predictionList) {
          prediction.add(Collections.singletonList(predicted));
        }
      }

      List<O> actual = item.getLabels();
      Preconditions.checkState(prediction.size() == actual.size());
      int numTagsCorrect = 0;
      int numLabels = 0;
      for (int i = 0; i < prediction.size(); i++) {
        if (prediction.get(i).contains((actual.get(i)))) {
          numTagsCorrect++;
        }
        numLabels += prediction.get(i).size();
      }

      System.out.println(item.getItems() + "\n" + prediction + "\n" + actual);
      int numSentencesCorrect = (numTagsCorrect == actual.size()) ? 1 : 0;
      return new SequenceTaggerError(numTagsCorrect, actual.size(), numLabels, numSentencesCorrect, 1);
    }
  }

  private static class SequenceTaggerEvaluationReducer extends SimpleReducer<SequenceTaggerError> {
    @Override
    public SequenceTaggerError getInitialValue() {
      return SequenceTaggerError.zero();
    }

    @Override
    public SequenceTaggerError reduce(SequenceTaggerError item, SequenceTaggerError accumulated) {
      accumulated.increment(item);
      return accumulated;
    }
  }
}
