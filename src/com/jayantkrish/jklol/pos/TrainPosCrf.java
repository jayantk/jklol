package com.jayantkrish.jklol.pos;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.cli.TrainedModelSet;
import com.jayantkrish.jklol.evaluation.Example;
import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.ObjectVariable;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.dynamic.DynamicVariableSet;
import com.jayantkrish.jklol.models.dynamic.VariableNamePattern;
import com.jayantkrish.jklol.models.loglinear.ConditionalLogLinearFactor;
import com.jayantkrish.jklol.models.loglinear.DiscreteLogLinearFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraphBuilder;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.pos.PosTaggedSentence.LocalContext;
import com.jayantkrish.jklol.preprocessing.DictionaryFeatureVectorGenerator;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.training.GradientOracle;
import com.jayantkrish.jklol.training.LoglikelihoodOracle;
import com.jayantkrish.jklol.training.MaxMarginOracle;
import com.jayantkrish.jklol.training.StochasticGradientTrainer;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IoUtils;

public class TrainPosCrf extends AbstractCli {
  
  public static final String PLATE_NAME = "plate";
  public static final String INPUT_NAME = "wordVector";
  public static final String OUTPUT_NAME = "pos";
  
  public static final String WORD_LABEL_FACTOR = "wordLabelFactor";
  public static final String TRANSITION_FACTOR = "transition";

  private OptionSpec<String> trainingFilename;
  // private OptionSpec<String> allowedTransitions;
  private OptionSpec<String> modelOutput;
  
  // Model construction options.
  private OptionSpec<Void> noTransitions;

  public TrainPosCrf() {
    super(CommonOptions.STOCHASTIC_GRADIENT, CommonOptions.MAP_REDUCE);
  }
  
  @Override
  public void initializeOptions(OptionParser parser) {
    trainingFilename = parser.accepts("training").withRequiredArg()
        .ofType(String.class).required();

    /*
    allowedTransitions = parser.accepts("transitions").withRequiredArg()
        .ofType(String.class);
        */
    
    modelOutput = parser.accepts("output").withRequiredArg().ofType(String.class).required();
    
    noTransitions = parser.accepts("noTransitions");
  }

  @Override
  public void run(OptionSet options) {
    // Read in the training data as sentences, to use for
    // feature generation.
    List<PosTaggedSentence> trainingData = readTrainingData(options.valueOf(trainingFilename));
    FeatureVectorGenerator<LocalContext> featureGen = buildFeatureVectorGenerator(trainingData);

    Set<String> posTags = Sets.newHashSet();
    for (PosTaggedSentence datum : trainingData) {
      posTags.addAll(datum.getPos());
    }

    System.out.println(posTags.size() + " POS tags");
    System.out.println(featureGen.getNumberOfFeatures() + " word/POS features");
    
    // Build the factor graph.
    ParametricFactorGraph sequenceModelFamily = buildFeaturizedSequenceModel(posTags,
        featureGen.getFeatureDictionary(), options.has(noTransitions));

    // Estimate parameters.
    List<Example<DynamicAssignment, DynamicAssignment>> examples = reformatTrainingData(trainingData,
        featureGen, sequenceModelFamily);
    SufficientStatistics parameters = estimateParameters(sequenceModelFamily, examples, false);

    // Save model to disk.
    System.out.println("Serializing trained model...");    
    DynamicFactorGraph factorGraph = sequenceModelFamily.getModelFromParameters(parameters);
    TrainedModelSet trainedModel = new TrainedPosTagger(sequenceModelFamily, parameters, 
        factorGraph, featureGen);
    IoUtils.serializeObjectToFile(trainedModel, options.valueOf(modelOutput));
  }

  private static FeatureVectorGenerator<LocalContext> buildFeatureVectorGenerator(List<PosTaggedSentence> sentences) {
    List<LocalContext> contexts = extractContextsFromData(sentences);
    WordContextFeatureGenerator wordGen = new WordContextFeatureGenerator();
    return DictionaryFeatureVectorGenerator.createFromData(contexts, wordGen, true);
  }
  
  private static List<PosTaggedSentence> readTrainingData(String trainingFilename) {
    List<PosTaggedSentence> sentences = Lists.newArrayList();
    for (String line : IoUtils.readLines(trainingFilename)) {
      sentences.add(PosTaggedSentence.parseFrom(line));
    }
    return sentences;
  }
  
  private static List<LocalContext> extractContextsFromData(List<PosTaggedSentence> sentences) {
    List<LocalContext> contexts = Lists.newArrayList();
    for (PosTaggedSentence sentence : sentences) {
      contexts.addAll(sentence.getLocalContexts());
    }
    return contexts;
  }
  
  /**
   * Converts training data as sentences into assignments that can be used 
   * for parameter estimation. 
   * 
   * @param sentences
   * @param featureGen
   * @param model
   * @return
   */
  public static List<Example<DynamicAssignment, DynamicAssignment>> reformatTrainingData(
      List<PosTaggedSentence> sentences, FeatureVectorGenerator<LocalContext> featureGen,
      ParametricFactorGraph model) {
    DynamicVariableSet plate = model.getVariables().getPlate(PLATE_NAME);
    VariableNumMap x = plate.getFixedVariables().getVariablesByName(INPUT_NAME);
    VariableNumMap y = plate.getFixedVariables().getVariablesByName(OUTPUT_NAME);

    List<Example<DynamicAssignment, DynamicAssignment>> examples = Lists.newArrayList();
    for (PosTaggedSentence sentence : sentences) { 
      List<Assignment> inputs = Lists.newArrayList();
      List<Assignment> outputs = Lists.newArrayList();
      
      List<LocalContext> contexts = sentence.getLocalContexts();
      List<String> posTags = sentence.getPos();
      for (int i = 0; i < contexts.size(); i ++) {
        inputs.add(x.outcomeArrayToAssignment(featureGen.apply(contexts.get(i))));
        outputs.add(y.outcomeArrayToAssignment(posTags.get(i)));
      }
      DynamicAssignment input = DynamicAssignment.createPlateAssignment(PLATE_NAME, inputs);
      DynamicAssignment output = DynamicAssignment.createPlateAssignment(PLATE_NAME, outputs);
      examples.add(Example.create(input, output));
    }

    return examples;
  }

  public static Example<DynamicAssignment, DynamicAssignment> reformatTrainingData(
      PosTaggedSentence sentence, FeatureVectorGenerator<LocalContext> featureGen,
      ParametricFactorGraph model) {
    List<PosTaggedSentence> sentences = Lists.newArrayList(sentence);
    List<Example<DynamicAssignment, DynamicAssignment>> examples = reformatTrainingData(
        sentences, featureGen, model);
    return examples.get(0);
  }

  public static ParametricFactorGraph buildFeaturizedSequenceModel(Set<String> posTags,
      DiscreteVariable featureDictionary, boolean noTransitions) {
    DiscreteVariable posType = new DiscreteVariable("pos", posTags);
    ObjectVariable wordVectorType = new ObjectVariable(Tensor.class);
    
    // Create a dynamic factor graph with a single plate replicating
    // the input/output variables.
    ParametricFactorGraphBuilder builder = new ParametricFactorGraphBuilder();
    builder.addPlate(PLATE_NAME, new VariableNumMap(Ints.asList(1, 2), 
        Arrays.asList(INPUT_NAME, OUTPUT_NAME), Arrays.<Variable>asList(wordVectorType, posType)),
        10000);
    String inputPattern = PLATE_NAME + "/?(0)/" + INPUT_NAME;
    String outputPattern = PLATE_NAME + "/?(0)/" + OUTPUT_NAME;
    String nextOutputPattern = PLATE_NAME + "/?(1)/" + OUTPUT_NAME;

    // Add a classifier from local word contexts to pos tags.
    VariableNumMap plateVars = new VariableNumMap(Ints.asList(1, 2),
        Arrays.asList(inputPattern, outputPattern), Arrays.<Variable>asList(wordVectorType, posType));
    VariableNumMap wordVectorVar = plateVars.getVariablesByName(inputPattern);
    VariableNumMap posVar = plateVars.getVariablesByName(outputPattern);
    ConditionalLogLinearFactor wordClassifier = new ConditionalLogLinearFactor(wordVectorVar, posVar,
        VariableNumMap.emptyMap(), featureDictionary);
    builder.addFactor(WORD_LABEL_FACTOR, wordClassifier, VariableNamePattern.fromTemplateVariables(
        plateVars, VariableNumMap.emptyMap()));
    
    // Add a factor connecting adjacent labels.
    if (!noTransitions) {
      VariableNumMap adjacentVars = new VariableNumMap(Ints.asList(0, 1),
          Arrays.asList(outputPattern, nextOutputPattern), Arrays.asList(posType, posType));
      builder.addFactor(TRANSITION_FACTOR, DiscreteLogLinearFactor.createIndicatorFactor(adjacentVars),
          VariableNamePattern.fromTemplateVariables(adjacentVars, VariableNumMap.emptyMap()));
    }

    return builder.build();
  }

  private SufficientStatistics estimateParameters(ParametricFactorGraph sequenceModel,
      List<Example<DynamicAssignment, DynamicAssignment>> trainingData,
      boolean useMaxMargin) {
    System.out.println(trainingData.size() + " training examples.");

    // Estimate parameters
    GradientOracle<DynamicFactorGraph, Example<DynamicAssignment, DynamicAssignment>> oracle;
    if (useMaxMargin) {
      oracle = new MaxMarginOracle(sequenceModel, new MaxMarginOracle.HammingCost(), new JunctionTree());
    } else {
      oracle = new LoglikelihoodOracle(sequenceModel, new JunctionTree());
    }

    System.out.println("Training...");
    StochasticGradientTrainer trainer = createStochasticGradientTrainer(trainingData.size());
    SufficientStatistics initialParameters = sequenceModel.getNewSufficientStatistics();
    initialParameters.makeDense();
    SufficientStatistics parameters = trainer.train(
        oracle, initialParameters, trainingData);

    return parameters;
  }
  
  public static void main(String[] args) {
    new TrainPosCrf().run(args);
  }
}
