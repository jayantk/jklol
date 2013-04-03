package com.jayantkrish.jklol.pos;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.boost.AveragingBoostingFamily;
import com.jayantkrish.jklol.boost.FunctionalGradientAscent;
import com.jayantkrish.jklol.boost.LoglikelihoodBoostingOracle;
import com.jayantkrish.jklol.boost.ParametricFactorGraphEnsemble;
import com.jayantkrish.jklol.boost.ParametricFactorGraphEnsembleBuilder;
import com.jayantkrish.jklol.boost.RegressionTreeBoostingFamily;
import com.jayantkrish.jklol.boost.SufficientStatisticsEnsemble;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.evaluation.Example;
import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.ObjectVariable;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.dynamic.VariableNamePattern;
import com.jayantkrish.jklol.pos.PosTaggedSentence.LocalContext;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.IoUtils;

public class TrainBoostedPosCrf extends AbstractCli {

  private OptionSpec<String> trainingFilename;
  private OptionSpec<String> modelOutput;
  
  // Model construction options.
  private OptionSpec<Void> noTransitions;
  private OptionSpec<Void> noUnknownWordFeatures;
  private OptionSpec<Integer> commonWordCountThreshold;
  private OptionSpec<String> initialModel;

  public TrainBoostedPosCrf() {
    super(CommonOptions.MAP_REDUCE, CommonOptions.FUNCTIONAL_GRADIENT_ASCENT,
        CommonOptions.REGRESSION_TREE);
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
    commonWordCountThreshold = parser.accepts("commonWordThreshold").withRequiredArg()
        .ofType(Integer.class).defaultsTo(5);
    initialModel = parser.accepts("initialModel").withRequiredArg().ofType(String.class);
  }

  @Override
  public void run(OptionSet options) {
    // Read in the training data as sentences, to use for
    // feature generation.
    List<PosTaggedSentence> trainingData = PosTaggerUtils.readTrainingData(
        options.valueOf(trainingFilename));
    
    TrainedPosTagger initialTagger = null;
    if (options.has(initialModel)) {
      initialTagger = IoUtils.readSerializedObject(options.valueOf(initialModel), TrainedPosTagger.class);
    }
    
    FeatureVectorGenerator<LocalContext> featureGen = null;
    
    if (options.has(initialModel)) {  
      featureGen = initialTagger.getFeatureGenerator();
    } else {
      featureGen = PosTaggerUtils.buildFeatureVectorGenerator(trainingData,
          options.valueOf(commonWordCountThreshold), options.has(noUnknownWordFeatures));
    }

    Set<String> posTags = Sets.newHashSet();
    for (PosTaggedSentence datum : trainingData) {
      posTags.addAll(datum.getPos());
    }

    System.out.println(posTags.size() + " POS tags");
    System.out.println(featureGen.getNumberOfFeatures() + " word/POS features");
    
    // Build the factor graph.
    ParametricFactorGraphEnsemble sequenceModelFamily = buildFeaturizedSequenceModel(posTags,
        featureGen.getFeatureDictionary(), options.has(noTransitions), initialTagger.getInstantiatedModel());

    // Estimate parameters.
    List<Example<DynamicAssignment, DynamicAssignment>> examples = PosTaggerUtils
        .reformatTrainingData(trainingData, featureGen, sequenceModelFamily.getVariables());
    SufficientStatisticsEnsemble parameters = estimateParameters(sequenceModelFamily, examples);

    // Save model to disk.
    System.out.println("Serializing trained model...");    
    DynamicFactorGraph factorGraph = sequenceModelFamily.getModelFromParameters(parameters);
    TrainedBoostedPosTagger trainedModel = new TrainedBoostedPosTagger(sequenceModelFamily, parameters, 
        factorGraph, featureGen);
    IoUtils.serializeObjectToFile(trainedModel, options.valueOf(modelOutput));
  }

  private ParametricFactorGraphEnsemble buildFeaturizedSequenceModel(Set<String> posTags,
      DiscreteVariable featureDictionary, boolean noTransitions, DynamicFactorGraph initialModel) {
    DiscreteVariable posType = new DiscreteVariable("pos", posTags);
    ObjectVariable wordVectorType = new ObjectVariable(Tensor.class);
    
    // Create a dynamic factor graph with a single plate replicating
    // the input/output variables.
    ParametricFactorGraphEnsembleBuilder builder = new ParametricFactorGraphEnsembleBuilder();
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
    RegressionTreeBoostingFamily wordClassifier = new RegressionTreeBoostingFamily(wordVectorVar, posVar,
        createRegressionTreeTrainer(), featureDictionary, null);
    Factor baseClassifier = null;
    if (initialModel != null) {
      baseClassifier = initialModel.getFactorByName(PosTaggerUtils.WORD_LABEL_FACTOR).getFactor();
      Preconditions.checkState(wordClassifier.getVariables().equals(baseClassifier.getVars()));
    }
    builder.addFactor(PosTaggerUtils.WORD_LABEL_FACTOR, wordClassifier, baseClassifier,
        VariableNamePattern.fromTemplateVariables(plateVars, VariableNumMap.emptyMap()));
    
    // Add a factor connecting adjacent labels.
    if (!noTransitions) {
      VariableNumMap adjacentVars = new VariableNumMap(Ints.asList(0, 1),
          Arrays.asList(outputPattern, nextOutputPattern), Arrays.asList(posType, posType));
      AveragingBoostingFamily transitionFamily = new AveragingBoostingFamily(adjacentVars);
      Factor baseTransitionFactor = null;
      if (initialModel != null) {
        baseTransitionFactor = initialModel.getFactorByName(PosTaggerUtils.TRANSITION_FACTOR).getFactor();
        Preconditions.checkState(transitionFamily.getVariables().equals(baseTransitionFactor.getVars()));
      }
      builder.addFactor(PosTaggerUtils.TRANSITION_FACTOR, transitionFamily, baseTransitionFactor,
          VariableNamePattern.fromTemplateVariables(adjacentVars, VariableNumMap.emptyMap()));
    }

    return builder.build();
  }

  private SufficientStatisticsEnsemble estimateParameters(ParametricFactorGraphEnsemble sequenceModel,
      List<Example<DynamicAssignment, DynamicAssignment>> trainingData) {
    System.out.println(trainingData.size() + " training examples.");

    // Estimate parameters using functional gradient ascent.
    System.out.println("Training...");
    SufficientStatisticsEnsemble initialParameters = sequenceModel.getNewSufficientStatistics();
    LoglikelihoodBoostingOracle oracle = new LoglikelihoodBoostingOracle(sequenceModel, new JunctionTree());
    FunctionalGradientAscent trainer = createFunctionalGradientAscent(trainingData.size());
    SufficientStatisticsEnsemble parameters = trainer.train(oracle, initialParameters, trainingData);
    return parameters;
  }
  
  public static void main(String[] args) {
    new TrainBoostedPosCrf().run(args);
  }
}
