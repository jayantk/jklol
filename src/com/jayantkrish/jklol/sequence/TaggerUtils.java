package com.jayantkrish.jklol.sequence;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

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
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.training.GradientOptimizer;
import com.jayantkrish.jklol.training.GradientOracle;
import com.jayantkrish.jklol.training.LoglikelihoodOracle;
import com.jayantkrish.jklol.training.MaxMarginOracle;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Utilities for constructing sequence taggers and formatting data for
 * sequence taggers.
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
      List<Assignment> outputs = Lists.newArrayList();

      List<LocalContext<I>> contexts = sequence.getLocalContexts();
      List<O> labels = sequence.getLabels();
      for (int i = 0; i < contexts.size(); i++) {
        inputs.add(x.outcomeArrayToAssignment(featureGen.apply(contexts.get(i))));
        outputs.add(y.outcomeArrayToAssignment(labels.get(i)));
      }
      DynamicAssignment input = DynamicAssignment.createPlateAssignment(PLATE_NAME, inputs);
      DynamicAssignment output = DynamicAssignment.createPlateAssignment(PLATE_NAME, outputs);
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

  private TaggerUtils() {
    // Prevent instantiation.
  }
}
