package com.jayantkrish.jklol.pos;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

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
import com.jayantkrish.jklol.models.dynamic.VariableNumPattern;
import com.jayantkrish.jklol.models.loglinear.ConditionalLogLinearFactor;
import com.jayantkrish.jklol.models.loglinear.IndicatorLogLinearFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraphBuilder;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.pos.PosTaggedSentence.LocalContext;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.training.GradientOracle;
import com.jayantkrish.jklol.training.LoglikelihoodOracle;
import com.jayantkrish.jklol.training.MaxMarginOracle;
import com.jayantkrish.jklol.training.StochasticGradientTrainer;
import com.jayantkrish.jklol.util.IoUtils;

/**
 * Trains a sequence model for part-of-speech tagging. The model can
 * either be trained as a conditional random field (CRF) or a
 * max-margin markov network (M^3N).
 * 
 * @author jayant
 */
public class TrainPosCrf extends AbstractCli {
  
  private OptionSpec<String> trainingFilename;
  // private OptionSpec<String> allowedTransitions;
  private OptionSpec<String> modelOutput;
  
  // Model construction options.
  private OptionSpec<Void> noTransitions;
  private OptionSpec<Void> noUnknownWordFeatures;
  private OptionSpec<Void> maxMargin;
  private OptionSpec<Integer> commonWordCountThreshold;

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
    noUnknownWordFeatures = parser.accepts("noUnknownWordFeatures");
    maxMargin = parser.accepts("maxMargin");
    commonWordCountThreshold = parser.accepts("commonWordThreshold").withRequiredArg()
        .ofType(Integer.class).defaultsTo(5);
  }

  @Override
  public void run(OptionSet options) {
    // Read in the training data as sentences, to use for
    // feature generation.
    List<PosTaggedSentence> trainingData = PosTaggerUtils.readTrainingData(
        options.valueOf(trainingFilename));
    FeatureVectorGenerator<LocalContext> featureGen = PosTaggerUtils.buildFeatureVectorGenerator(trainingData,
        options.valueOf(commonWordCountThreshold), options.has(noUnknownWordFeatures));

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
    List<Example<DynamicAssignment, DynamicAssignment>> examples = PosTaggerUtils
        .reformatTrainingData(trainingData, featureGen, sequenceModelFamily.getVariables());
    SufficientStatistics parameters = estimateParameters(sequenceModelFamily, examples, options.has(maxMargin));

    // Save model to disk.
    System.out.println("Serializing trained model...");    
    DynamicFactorGraph factorGraph = sequenceModelFamily.getModelFromParameters(parameters);
    TrainedModelSet trainedModel = new TrainedPosTagger(sequenceModelFamily, parameters, 
        factorGraph, featureGen);
    IoUtils.serializeObjectToFile(trainedModel, options.valueOf(modelOutput));
  }

  private static ParametricFactorGraph buildFeaturizedSequenceModel(Set<String> posTags,
      DiscreteVariable featureDictionary, boolean noTransitions) {
    DiscreteVariable posType = new DiscreteVariable("pos", posTags);
    ObjectVariable wordVectorType = new ObjectVariable(Tensor.class);
    
    // Create a dynamic factor graph with a single plate replicating
    // the input/output variables.
    ParametricFactorGraphBuilder builder = new ParametricFactorGraphBuilder();
    builder.addPlate(PosTaggerUtils.PLATE_NAME, new VariableNumMap(Ints.asList(1, 2), 
        Arrays.asList(PosTaggerUtils.INPUT_NAME, PosTaggerUtils.OUTPUT_NAME),
        Arrays.<Variable>asList(wordVectorType, posType)), 10000);
    String inputPattern = PosTaggerUtils.PLATE_NAME + "/?(0)/" + PosTaggerUtils.INPUT_NAME;
    String outputPattern = PosTaggerUtils.PLATE_NAME + "/?(0)/" + PosTaggerUtils.OUTPUT_NAME;
    String nextOutputPattern = PosTaggerUtils.PLATE_NAME + "/?(1)/" + PosTaggerUtils.OUTPUT_NAME;

    // Add a classifier from local word contexts to pos tags.
    VariableNumMap plateVars = new VariableNumMap(Ints.asList(1, 2),
        Arrays.asList(inputPattern, outputPattern), Arrays.<Variable>asList(wordVectorType, posType));
    VariableNumMap wordVectorVar = plateVars.getVariablesByName(inputPattern);
    VariableNumMap posVar = plateVars.getVariablesByName(outputPattern);
    ConditionalLogLinearFactor wordClassifier = new ConditionalLogLinearFactor(wordVectorVar, posVar,
        VariableNumMap.emptyMap(), featureDictionary);
    builder.addFactor(PosTaggerUtils.WORD_LABEL_FACTOR, wordClassifier,
        VariableNumPattern.fromTemplateVariables(plateVars, VariableNumMap.emptyMap(), builder.getDynamicVariableSet()));
    
    // Add a factor connecting adjacent labels.
    if (!noTransitions) {
      VariableNumMap adjacentVars = new VariableNumMap(Ints.asList(0, 1),
          Arrays.asList(outputPattern, nextOutputPattern), Arrays.asList(posType, posType));
      builder.addFactor(PosTaggerUtils.TRANSITION_FACTOR, IndicatorLogLinearFactor.createDenseFactor(adjacentVars),
          VariableNumPattern.fromTemplateVariables(adjacentVars, VariableNumMap.emptyMap(), builder.getDynamicVariableSet()));
    }

    return builder.build();
  }

  private SufficientStatistics estimateParameters(ParametricFactorGraph sequenceModel,
      List<Example<DynamicAssignment, DynamicAssignment>> trainingData,
      boolean useMaxMargin) {
    System.out.println(trainingData.size() + " training examples.");

    // Estimate parameters
    GradientOracle<DynamicFactorGraph, Example<DynamicAssignment, DynamicAssignment>> oracle;
    SufficientStatistics initialParameters = sequenceModel.getNewSufficientStatistics();
    initialParameters.makeDense();
    System.out.println("Training...");
    if (useMaxMargin) {
      oracle = new MaxMarginOracle(sequenceModel, new MaxMarginOracle.HammingCost(), new JunctionTree());

      StochasticGradientTrainer trainer = createStochasticGradientTrainer(trainingData.size());
      SufficientStatistics parameters = trainer.train(oracle, initialParameters, trainingData);
      return parameters;
    } else {
      oracle = new LoglikelihoodOracle(sequenceModel, new JunctionTree());

      /*
      Lbfgs lbfgs = new Lbfgs(50, 10, 0.0, new DefaultLogFunction(1, false));
      SufficientStatistics parameters = lbfgs.train(oracle, initialParameters, trainingData);
      */
      StochasticGradientTrainer trainer = createStochasticGradientTrainer(trainingData.size());
      SufficientStatistics parameters = trainer.train(oracle, initialParameters, trainingData);
      return parameters;
    }
  }
  
  public static void main(String[] args) {
    new TrainPosCrf().run(args);
  }
}
