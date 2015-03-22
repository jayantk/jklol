package com.jayantkrish.jklol.sequence;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.evaluation.Example;
import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.ObjectVariable;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.dynamic.DynamicVariableSet;
import com.jayantkrish.jklol.models.dynamic.ReplicatedFactor;
import com.jayantkrish.jklol.models.dynamic.VariableNumPattern;
import com.jayantkrish.jklol.models.loglinear.ParametricLinearClassifierFactor;
import com.jayantkrish.jklol.models.loglinear.DenseIndicatorLogLinearFactor;
import com.jayantkrish.jklol.models.parametric.ConstantParametricFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraphBuilder;
import com.jayantkrish.jklol.models.parametric.ParametricNormalizingFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.parallel.MapReduceConfiguration;
import com.jayantkrish.jklol.parallel.MapReduceExecutor;
import com.jayantkrish.jklol.parallel.Mapper;
import com.jayantkrish.jklol.parallel.Reducer.SimpleReducer;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.tensor.SparseTensor;
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
  public static final String INPUT_FEATURES_NAME = "inputFeatures";
  public static final String INPUT_NAME = "input";
  public static final String OUTPUT_NAME = "label";

  public static final String WORD_LABEL_FACTOR = "wordLabelFactor";
  public static final String LABEL_RESTRICTION_FACTOR = "labelRestrictionFactor";
  public static final String TRANSITION_FACTOR = "transition";
  public static final String NORMALIZED_FACTOR = "normalizedFactors";
  
  public static final String DEFAULT_INPUT_VALUE="*DEFAULT*";
  
  // Names of the variables in factors of generated sequence
  // models.
  public static final String INPUT_FEATURES_PATTERN = TaggerUtils.PLATE_NAME + "/?(1)/" + TaggerUtils.INPUT_FEATURES_NAME;
  public static final String INPUT_PATTERN = TaggerUtils.PLATE_NAME + "/?(1)/" + TaggerUtils.INPUT_NAME;
  public static final String PREV_OUTPUT_PATTERN = TaggerUtils.PLATE_NAME + "/?(0)/" + TaggerUtils.OUTPUT_NAME;
  public static final String OUTPUT_PATTERN = TaggerUtils.PLATE_NAME + "/?(1)/" + TaggerUtils.OUTPUT_NAME;

  public static <I, O> List<LocalContext<I>> extractContextsFromData(
      List<? extends TaggedSequence<I, O>> sequences) {
    List<LocalContext<I>> contexts = Lists.newArrayList();
    for (TaggedSequence<I, O> sequence : sequences) {
      contexts.addAll(sequence.getLocalContexts());
    }
    return contexts;
  }
  
  public static Function<Object, String> getDefaultInputGenerator() {
    return Functions.constant(DEFAULT_INPUT_VALUE);
  }

  public static <I, O> Example<DynamicAssignment, DynamicAssignment> reformatTrainingData(
      TaggedSequence<I, O> sequence, FeatureVectorGenerator<LocalContext<I>> featureGen,
      DynamicVariableSet modelVariables, I startInput, O startLabel) {
    return reformatTrainingData(sequence, featureGen, getDefaultInputGenerator(), modelVariables,
        startInput, startLabel);
  }

  public static <I, O> Example<DynamicAssignment, DynamicAssignment> reformatTrainingData(
      TaggedSequence<I, O> sequence, FeatureVectorGenerator<LocalContext<I>> featureGen,
      Function<? super LocalContext<I>, ? extends Object> inputGen, DynamicVariableSet modelVariables,
      I startInput, O startLabel) {
    List<TaggedSequence<I, O>> sequences = Lists.newArrayList();
    sequences.add(sequence);
    List<Example<DynamicAssignment, DynamicAssignment>> examples = reformatTrainingData(
        sequences, featureGen, inputGen, modelVariables, startInput, startLabel);
    return examples.get(0);
  }
  
  public static <I, O> List<Example<DynamicAssignment, DynamicAssignment>> reformatTrainingData(
      List<? extends TaggedSequence<I, O>> sequences, FeatureVectorGenerator<LocalContext<I>> featureGen,
      DynamicVariableSet modelVariables, I startInput, O startLabel) {
    return reformatTrainingData(sequences, featureGen, getDefaultInputGenerator(), modelVariables,
        startInput, startLabel);
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
      Function<? super LocalContext<I>, ? extends Object> inputGen, DynamicVariableSet modelVariables,
      I startInput, O startLabel) {
    Preconditions.checkArgument(!(startInput == null ^ startLabel == null));

    DynamicVariableSet plate = modelVariables.getPlate(PLATE_NAME);
    VariableNumMap x = plate.getFixedVariables().getVariablesByName(INPUT_FEATURES_NAME);
    VariableNumMap xInput = plate.getFixedVariables().getVariablesByName(INPUT_NAME);
    VariableNumMap y = plate.getFixedVariables().getVariablesByName(OUTPUT_NAME);

    List<Example<DynamicAssignment, DynamicAssignment>> examples = Lists.newArrayList();
    for (TaggedSequence<I, O> sequence : sequences) {
      List<Assignment> inputs = Lists.newArrayList();
      
      if (startInput != null) {
        List<I> newItems = Lists.newArrayList();
        newItems.add(startInput);
        newItems.addAll(sequence.getItems());
        LocalContext<I> startContext = new ListLocalContext<I>(newItems, 0);
        
        Assignment inputFeatureVector = x.outcomeArrayToAssignment(featureGen.apply(startContext));
        Assignment inputElement = xInput.outcomeArrayToAssignment(inputGen.apply(startContext));
        Assignment firstLabel = y.outcomeArrayToAssignment(startLabel);
        inputs.add(Assignment.unionAll(inputFeatureVector, inputElement, firstLabel));
      }
      
      List<LocalContext<I>> contexts = sequence.getLocalContexts();
      for (int i = 0; i < contexts.size(); i++) {
        Assignment inputFeatureVector = x.outcomeArrayToAssignment(featureGen.apply(contexts.get(i)));
        Assignment inputElement = xInput.outcomeArrayToAssignment(inputGen.apply(contexts.get(i)));
        inputs.add(inputFeatureVector.union(inputElement));
      }
      DynamicAssignment input = DynamicAssignment.createPlateAssignment(PLATE_NAME, inputs);

      DynamicAssignment output = DynamicAssignment.EMPTY;
      if (sequence.getLabels() != null) {
        List<Assignment> outputs = Lists.newArrayList();
        
        if (startInput != null) {
          // First label is given (and equal to the special start label).
          outputs.add(Assignment.EMPTY);
        }

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
   * Creates training examples from sequential data where each example
   * involves predicting a single label given the current input and
   * previous label. Such examples are suitable for training
   * locally-normalized sequence models, such as HMMs and MEMMs.
   *  
   * @param sequences
   * @param featureGen
   * @param inputGen
   * @param modelVariables
   * @return
   */
  public static <I, O> List<Example<DynamicAssignment, DynamicAssignment>> reformatTrainingDataPerItem(
      List<? extends TaggedSequence<I, O>> sequences, FeatureVectorGenerator<LocalContext<I>> featureGen,
      Function<? super LocalContext<I>, ? extends Object> inputGen, DynamicVariableSet modelVariables,
      I startInput, O startLabel) {
    DynamicVariableSet plate = modelVariables.getPlate(PLATE_NAME);
    VariableNumMap x = plate.getFixedVariables().getVariablesByName(INPUT_FEATURES_NAME);
    VariableNumMap xInput = plate.getFixedVariables().getVariablesByName(INPUT_NAME);
    VariableNumMap y = plate.getFixedVariables().getVariablesByName(OUTPUT_NAME);

    ReformatPerItemMapper<I, O> mapper = new ReformatPerItemMapper<I, O>(featureGen, inputGen, x, xInput, y,
        startInput, startLabel);
    List<List<Example<DynamicAssignment, DynamicAssignment>>> exampleLists = MapReduceConfiguration
        .getMapReduceExecutor().map(sequences, mapper);

    List<Example<DynamicAssignment, DynamicAssignment>> examples = Lists.newArrayList();
    for (List<Example<DynamicAssignment, DynamicAssignment>> exampleList : exampleLists) {
      examples.addAll(exampleList);
    }
    return examples;
  }
  
  public static <O> ParametricFactorGraph buildFeaturizedSequenceModel(Set<O> labels, 
      DiscreteVariable featureDictionary, boolean noTransitions, boolean locallyNormalized) {
    DiscreteVariable inputType = new DiscreteVariable("inputs", Lists.newArrayList(DEFAULT_INPUT_VALUE));
    DiscreteVariable labelType = new DiscreteVariable("labels", labels);

    VariableNumMap vars = new VariableNumMap(Ints.asList(0, 1), Lists.newArrayList("inputs", "labels"),
        Lists.newArrayList(inputType, labelType)); 
    TableFactor labelRestrictions = TableFactor.unity(vars);
    
    return buildFeaturizedSequenceModel(inputType, labelType, featureDictionary,
        labelRestrictions.getWeights(), noTransitions, locallyNormalized);
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
   * @param labelType
   * @param featureDictionary
   * @param labelRestrictions
   * @param noTransitions
   * @param locallyNormalized
   * @return
   */
  public static <O> ParametricFactorGraph buildFeaturizedSequenceModel(DiscreteVariable inputType, 
      DiscreteVariable labelType, DiscreteVariable featureDictionary, Tensor labelRestrictions,
      boolean noTransitions, boolean locallyNormalized) {
    ObjectVariable inputVectorType = new ObjectVariable(Tensor.class);

    // Create a dynamic factor graph with a single plate replicating
    // the input/output variables.
    ParametricFactorGraphBuilder builder = new ParametricFactorGraphBuilder();
    builder.addPlate(TaggerUtils.PLATE_NAME, new VariableNumMap(Ints.asList(1, 2, 4),
        Arrays.asList(TaggerUtils.INPUT_FEATURES_NAME, TaggerUtils.INPUT_NAME, TaggerUtils.OUTPUT_NAME),
        Arrays.<Variable> asList(inputVectorType, inputType, labelType)), 10000);

    // Create a classifier from local word contexts to pos tags.
    VariableNumMap classifierVars = new VariableNumMap(Ints.asList(1, 4),
        Arrays.asList(INPUT_FEATURES_PATTERN, OUTPUT_PATTERN), Arrays.<Variable> asList(inputVectorType, labelType));
    VariableNumMap wordVectorVar = classifierVars.getVariablesByName(INPUT_FEATURES_PATTERN);
    VariableNumMap posVar = classifierVars.getVariablesByName(OUTPUT_PATTERN);
    ParametricLinearClassifierFactor wordClassifier = new ParametricLinearClassifierFactor(wordVectorVar, posVar,
        VariableNumMap.EMPTY, featureDictionary, false);

    // Create a constant factor for encoding label restrictions.
    VariableNumMap restrictionVars = new VariableNumMap(Ints.asList(2, 4),
        Arrays.asList(INPUT_PATTERN, OUTPUT_PATTERN), Arrays.<Variable> asList(inputType, labelType));
    VariableNumMap wordVar = restrictionVars.getVariablesByName(INPUT_PATTERN);
    VariableNumMap labelVar = restrictionVars.getVariablesByName(OUTPUT_PATTERN);
    DiscreteFactor restrictions = new TableFactor(wordVar.union(labelVar), labelRestrictions.relabelDimensions(new int[] {2, 4}));

    // Create a factor connecting adjacent labels.
    VariableNumMap adjacentVars = new VariableNumMap(Ints.asList(3, 4),
        Arrays.asList(PREV_OUTPUT_PATTERN, OUTPUT_PATTERN), Arrays.asList(labelType, labelType));
    ParametricFactor adjacentFactor = null;
    if (!noTransitions) {
      adjacentFactor = new DenseIndicatorLogLinearFactor(adjacentVars, false);
    }

    if (locallyNormalized) {
      // Add a factor that normalizes the distribution.
      List<ParametricFactor> factors = Lists.<ParametricFactor>newArrayList(
          new ConstantParametricFactor(restrictions.getVars(), restrictions), wordClassifier);
      VariableNumMap inputVars = wordVectorVar.union(wordVar);
      VariableNumMap conditionalVars = VariableNumMap.EMPTY;
      VariableNumMap outputVar = adjacentVars.getVariablesByName(OUTPUT_PATTERN);
      if (adjacentFactor != null) {
        factors.add(adjacentFactor);
        conditionalVars = adjacentVars.getVariablesByName(PREV_OUTPUT_PATTERN);
      }
      VariableNumMap factorVars = VariableNumMap.unionAll(inputVars, conditionalVars, outputVar);
      ParametricNormalizingFactor normalizingFactor = new ParametricNormalizingFactor(
          inputVars, conditionalVars, outputVar, factors);
      builder.addFactor(NORMALIZED_FACTOR, normalizingFactor,
          VariableNumPattern.fromTemplateVariables(factorVars, VariableNumMap.EMPTY, builder.getDynamicVariableSet()));
    } else {
      // Just add each factor to the factor graph 
      builder.addFactor(TaggerUtils.WORD_LABEL_FACTOR, wordClassifier,
          VariableNumPattern.fromTemplateVariables(classifierVars, VariableNumMap.EMPTY, builder.getDynamicVariableSet()));
      builder.addConstantFactor(TaggerUtils.LABEL_RESTRICTION_FACTOR, new ReplicatedFactor(restrictions,
          VariableNumPattern.fromTemplateVariables(restrictionVars, VariableNumMap.EMPTY, builder.getDynamicVariableSet())));
      if (adjacentFactor != null) {
        builder.addFactor(TaggerUtils.TRANSITION_FACTOR, adjacentFactor,
            VariableNumPattern.fromTemplateVariables(adjacentVars, VariableNumMap.EMPTY, builder.getDynamicVariableSet()));
      }
    }
    return builder.build();
  }

  public static <I, O> FactorGraphSequenceTagger<I, O> trainSequenceModel(
      ParametricFactorGraph sequenceModelFamily, List<? extends TaggedSequence<I, O>> trainingData,
          Class<O> outputClass, FeatureVectorGenerator<LocalContext<I>> featureGen, 
          I startInput, O startLabel, GradientOptimizer optimizer, boolean useMaxMargin) {
    
    List<Example<DynamicAssignment, DynamicAssignment>> examples = TaggerUtils
        .reformatTrainingData(trainingData, featureGen, getDefaultInputGenerator(),
            sequenceModelFamily.getVariables(), startInput, startLabel);
    return trainSequenceModel(sequenceModelFamily, examples, outputClass, featureGen,
       getDefaultInputGenerator(), startInput, startLabel, optimizer, useMaxMargin);
  }

  /**
   * Trains a sequence model.
   * 
   * @param sequenceModelFamily
   * @param trainingData
   * @param outputClass
   * @param featureGen
   * @param inputGen
   * @param optimizer
   * @param useMaxMargin
   * @return
   */
  public static <I, O> FactorGraphSequenceTagger<I, O> trainSequenceModel(
      ParametricFactorGraph sequenceModelFamily, List<Example<DynamicAssignment, DynamicAssignment>> examples,
      Class<O> outputClass, FeatureVectorGenerator<LocalContext<I>> featureGen, 
      Function<? super LocalContext<I>, ? extends Object> inputGen, I startInput, O startLabel,
      GradientOptimizer optimizer, boolean useMaxMargin) {

    // Generate the training data and estimate parameters.
    SufficientStatistics parameters = estimateParameters(sequenceModelFamily, examples,
        optimizer, useMaxMargin);

    DynamicFactorGraph factorGraph = sequenceModelFamily.getModelFromParameters(parameters);
    return new FactorGraphSequenceTagger<I, O>(sequenceModelFamily, parameters,
        factorGraph, featureGen, inputGen, outputClass, new JunctionTree(), new JunctionTree(true), 
        startInput, startLabel);
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

      StringBuilder sb = new StringBuilder();
      sb.append(item.getItems());
      sb.append("\n");
      for (int i = 0; i < item.getItems().size(); i++) {
        sb.append(item.getItems().get(i) + "\t" + actual.get(i) + "\t" + prediction.get(i));
        if (!prediction.get(i).contains(actual.get(i))) {
          sb.append("\t" + "INCORRECT");
        }
        sb.append("\n");
      }
      
      System.out.println(sb.toString());
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

  private static class ReformatPerItemMapper<I, O> extends Mapper<TaggedSequence<I, O>, List<Example<DynamicAssignment, DynamicAssignment>>> {
    private final FeatureVectorGenerator<LocalContext<I>> featureGen;
    private final Function<? super LocalContext<I>, ? extends Object> inputGen;
    
    private final VariableNumMap x;
    private final VariableNumMap xInput;
    private final VariableNumMap y;
    
    private final I startInput;
    private final O startLabel;
    
    public ReformatPerItemMapper(FeatureVectorGenerator<LocalContext<I>> featureGen,
        Function<? super LocalContext<I>, ? extends Object> inputGen, VariableNumMap x,
        VariableNumMap xInput, VariableNumMap y, I startInput, O startLabel) {
      super();
      this.featureGen = Preconditions.checkNotNull(featureGen);
      this.inputGen = Preconditions.checkNotNull(inputGen);
      this.x = Preconditions.checkNotNull(x);
      this.xInput = Preconditions.checkNotNull(xInput);
      this.y = Preconditions.checkNotNull(y);
      
      this.startInput = startInput;
      this.startLabel = startLabel;
      
      // Either both or neither are null. 
      Preconditions.checkArgument(!(startInput == null ^ startLabel == null));
    }

    @Override
    public List<Example<DynamicAssignment, DynamicAssignment>> map(TaggedSequence<I, O> sequence) {
      List<Example<DynamicAssignment, DynamicAssignment>> examples = Lists.newArrayList();

      List<LocalContext<I>> contexts = sequence.getLocalContexts();
      if (startInput != null) {
        List<I> itemsWithStart = Lists.newArrayList();
        itemsWithStart.add(startInput);
        itemsWithStart.addAll(sequence.getItems());
        contexts = (new ListTaggedSequence<I, O>(itemsWithStart, null)).getLocalContexts();
      }

      Preconditions.checkArgument(sequence.getLabels() != null);
      List<O> labels = sequence.getLabels();
      if (startLabel != null) {
        List<O> newLabels = Lists.newArrayList();
        newLabels.add(startLabel);
        newLabels.addAll(labels);
        labels = newLabels;
      }

      // Each training example consists of a pair of adjacent input and label variables.
      // All inputs and the first label are always conditioned on, while the second
      // label is to be predicted. The first input vector is set to 0, since the 
      // gradient for that input will be 0 no matter what the input vector is.
      Tensor zeroVector = SparseTensor.empty(new int[] {0}, new int[] {featureGen.getNumberOfFeatures()});
      
      Assignment prevFeatureVector = x.outcomeArrayToAssignment(zeroVector);
      Assignment prevInputElement = null, prevLabel = null;
      for (int i = 0; i < contexts.size(); i++) {
        List<Assignment> inputList = Lists.newArrayList();
        List<Assignment> outputList = Lists.newArrayList();
        if (i > 0) {
          // The first assignment requires special handling. Either we should
          // append the start symbol, or simply create an assignment without
          // a previous label.
          inputList.add(Assignment.unionAll(prevFeatureVector, prevInputElement, prevLabel));
          outputList.add(Assignment.EMPTY);
        }

        Assignment inputFeatureVector = x.outcomeArrayToAssignment(featureGen.apply(contexts.get(i)));
        Assignment inputElement = xInput.outcomeArrayToAssignment(inputGen.apply(contexts.get(i)));
        inputList.add(inputElement.union(inputFeatureVector));

        Assignment output = y.outcomeArrayToAssignment(labels.get(i));
        outputList.add(output);

        if (i != 0 || startInput == null ) {
          // if startInput is non-null, then the first item is the special
          // start label.
          examples.add(Example.create(DynamicAssignment.createPlateAssignment(PLATE_NAME, inputList),
                                      DynamicAssignment.createPlateAssignment(PLATE_NAME, outputList)));
        }

        prevInputElement = inputElement;
        prevLabel = output;
      }

      return examples;
    }
  }
}
