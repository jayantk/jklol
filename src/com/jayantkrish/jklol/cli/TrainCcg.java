package com.jayantkrish.jklol.cli;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.CcgBeamSearchInference;
import com.jayantkrish.jklol.ccg.CcgExactInference;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.CcgFeatureFactory;
import com.jayantkrish.jklol.ccg.CcgInference;
import com.jayantkrish.jklol.ccg.CcgLoglikelihoodOracle;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.CcgParserUtils;
import com.jayantkrish.jklol.ccg.CcgPerceptronOracle;
import com.jayantkrish.jklol.ccg.CcgRuleSchema;
import com.jayantkrish.jklol.ccg.CcgSyntaxTree;
import com.jayantkrish.jklol.ccg.DefaultCcgFeatureFactory;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.ccg.SyntacticCategory;
import com.jayantkrish.jklol.ccg.data.CcgExampleFormat;
import com.jayantkrish.jklol.ccg.data.CcgSyntaxTreeFormat;
import com.jayantkrish.jklol.ccg.data.CcgbankSyntaxTreeFormat;
import com.jayantkrish.jklol.ccg.supertag.SupertaggedSentence;
import com.jayantkrish.jklol.ccg.supertag.Supertagger;
import com.jayantkrish.jklol.data.DataFormat;
import com.jayantkrish.jklol.models.parametric.ListSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.parallel.MapReduceConfiguration;
import com.jayantkrish.jklol.parallel.MapReduceExecutor;
import com.jayantkrish.jklol.parallel.Mapper;
import com.jayantkrish.jklol.training.GradientOptimizer;
import com.jayantkrish.jklol.training.GradientOracle;
import com.jayantkrish.jklol.util.IoUtils;

/**
 * Estimates parameters for a CCG parser given a lexicon and a set of
 * training data. The training data consists of sentences with
 * annotations of the correct dependency structures.
 * 
 * @author jayantk
 */
public class TrainCcg extends AbstractCli {

  private OptionSpec<String> trainingData;
  private OptionSpec<String> modelOutput;

  private OptionSpec<String> syntaxMap;
  private OptionSpec<Integer> beamSize;
  private OptionSpec<Long> maxParseTimeMillis;
  private OptionSpec<String> supertagger;
  private OptionSpec<Double> multitagThreshold;
  private OptionSpec<Integer> featureCountThreshold;
  private OptionSpec<Void> useCcgBankFormat;
  private OptionSpec<Void> perceptron;
  private OptionSpec<Void> discardInvalid;
  private OptionSpec<Void> ignoreSemantics;
  private OptionSpec<Void> onlyObservedBinaryRules;
  private OptionSpec<Void> exactInference;

  public TrainCcg() {
    super(CommonOptions.STOCHASTIC_GRADIENT, CommonOptions.MAP_REDUCE,
        CommonOptions.PARAMETRIC_CCG_PARSER);
  }

  @Override
  public void initializeOptions(OptionParser parser) {
    // Required arguments.
    trainingData = parser.accepts("trainingData").withRequiredArg().ofType(String.class).required();
    modelOutput = parser.accepts("output").withRequiredArg().ofType(String.class).required();

    // Optional options
    syntaxMap = parser.accepts("syntaxMap").withRequiredArg().ofType(String.class);
    beamSize = parser.accepts("beamSize").withRequiredArg().ofType(Integer.class).defaultsTo(100);
    maxParseTimeMillis = parser.accepts("maxParseTimeMillis").withRequiredArg().ofType(Long.class).defaultsTo(-1L);
    supertagger = parser.accepts("supertagger").withRequiredArg().ofType(String.class);
    multitagThreshold = parser.accepts("multitagThreshold").withRequiredArg().ofType(Double.class);
    useCcgBankFormat = parser.accepts("useCcgBankFormat");
    featureCountThreshold = parser.accepts("featureCountThreshold").withRequiredArg().ofType(Integer.class);
    perceptron = parser.accepts("perceptron");
    discardInvalid = parser.accepts("discardInvalid");
    ignoreSemantics = parser.accepts("ignoreSemantics");
    onlyObservedBinaryRules = parser.accepts("onlyObservedBinaryRules");
    exactInference = parser.accepts("exactInference");
  }

  @Override
  public void run(OptionSet options) {
    List<CcgExample> unfilteredTrainingExamples = readTrainingData(options.valueOf(trainingData),
        options.has(ignoreSemantics), options.has(useCcgBankFormat), options.valueOf(syntaxMap));
    Set<String> posTags = CcgExample.getPosTagVocabulary(unfilteredTrainingExamples);
    System.out.println(posTags.size() + " POS tags");

    if (options.has(supertagger)) {
      Preconditions.checkState(options.has(multitagThreshold));
      Supertagger supertaggerModel = IoUtils.readSerializedObject(options.valueOf(supertagger), Supertagger.class);
      unfilteredTrainingExamples = supertagExamples(unfilteredTrainingExamples, supertaggerModel,
          options.valueOf(multitagThreshold), true);
    }

    Set<CcgRuleSchema> observedRules = null;
    if (options.has(onlyObservedBinaryRules)) {
      observedRules = Sets.newHashSet();
      for (CcgExample example : unfilteredTrainingExamples) {
        observedRules.addAll(example.getSyntacticParse().getObservedBinaryRules());
      }
    }

    // Create the CCG parser from the provided options.
    System.out.println("Creating ParametricCcgParser.");
    CcgFeatureFactory featureFactory = new DefaultCcgFeatureFactory(null);
    ParametricCcgParser family = createCcgParser(posTags, observedRules, featureFactory);
    System.out.println("Done creating ParametricCcgParser.");

    // Read in training data and confirm its validity.
    CcgParser parser = family.getModelFromParameters(family.getNewSufficientStatistics());

    System.out.println(parser.getSyntaxDistribution().getParameterDescription());

    Multimap<SyntacticCategory, HeadedSyntacticCategory> syntaxMultimap = parser.getSyntacticCategoryMap();
    List<CcgExample> trainingExamples = CcgParserUtils.filterExampleCollection(parser, 
        unfilteredTrainingExamples, !options.has(discardInvalid), syntaxMultimap);
    System.out.println(trainingExamples.size() + " training examples.");
    int numDiscarded = unfilteredTrainingExamples.size() - trainingExamples.size();
    System.out.println(numDiscarded + " discarded training examples.");

    if (options.has(featureCountThreshold)) {
      family = applyFeatureCountThreshold(family, trainingExamples, options.valueOf(featureCountThreshold)); 
    }

    if (options.has(logParametersDir)) {
      IoUtils.serializeObjectToFile(family, options.valueOf(logParametersDir) + File.pathSeparator + "family.ser");
    }

    // Train the model.
    GradientOracle<CcgParser, CcgExample> oracle = null;
    if (options.has(perceptron)) {
      CcgInference inferenceAlgorithm = null;
      if (options.has(exactInference)) {
        inferenceAlgorithm = new CcgExactInference(null, options.valueOf(maxParseTimeMillis));
      } else {
        inferenceAlgorithm = new CcgBeamSearchInference(null, options.valueOf(beamSize), options.valueOf(maxParseTimeMillis), true);
      }
      oracle = new CcgPerceptronOracle(family, inferenceAlgorithm);
    } else {
      oracle = new CcgLoglikelihoodOracle(family, options.valueOf(beamSize));
    }
    GradientOptimizer trainer = createGradientOptimizer(trainingExamples.size());
    SufficientStatistics parameters = trainer.train(oracle, oracle.initializeGradient(),
        trainingExamples);
    CcgParser ccgParser = family.getModelFromParameters(parameters);

    System.out.println("Serializing trained model...");
    IoUtils.serializeObjectToFile(ccgParser, options.valueOf(modelOutput));

    System.out.println("Trained model parameters:");
    System.out.println(family.getParameterDescription(parameters, 10000));
  }

  public static List<CcgExample> readTrainingData(String filename, boolean ignoreSemantics,
      boolean useCcgBankFormat, String syntacticCategoryMapFilename) {
    // Read in all of the provided training examples.
    DataFormat<CcgSyntaxTree> syntaxTreeReader = null;
    if (useCcgBankFormat) {
      Map<SyntacticCategory, HeadedSyntacticCategory> syntacticCategoryMap;
      if (syntacticCategoryMapFilename != null) {
        syntacticCategoryMap = readSyntaxMap(syntacticCategoryMapFilename);
      } else {
        syntacticCategoryMap = Maps.newHashMap();
      }

      syntaxTreeReader = new CcgbankSyntaxTreeFormat(syntacticCategoryMap,
          CcgbankSyntaxTreeFormat.DEFAULT_CATEGORIES_TO_STRIP);
    } else {
      syntaxTreeReader = new CcgSyntaxTreeFormat();
    }
    CcgExampleFormat exampleReader = new CcgExampleFormat(syntaxTreeReader, ignoreSemantics);
    return exampleReader.parseFromFile(filename);
  }

  private static List<CcgExample> supertagExamples(List<CcgExample> examples,
      Supertagger supertagger, double multitagThreshold, boolean includeGoldSupertags) {
    System.out.println("Supertagging examples...");
    MapReduceExecutor executor = MapReduceConfiguration.getMapReduceExecutor();
    List<CcgExample> newExamples = executor.map(examples,
        new SupertaggerMapper(supertagger, multitagThreshold, includeGoldSupertags));
    System.out.println("Done supertagging.");
    return newExamples;
  }

  private static ParametricCcgParser applyFeatureCountThreshold(ParametricCcgParser family,
      List<CcgExample> examples, double featureCountThreshold) {
    System.out.println("Calculating feature counts...");
    // Count the number of occurrences of each feature in the gold standard
    // CCG parses, then find all of features which occur >= a threshold.
    SufficientStatistics featureCounts = CcgParserUtils.getFeatureCounts(family, examples);
    featureCounts.findEntriesLargerThan(featureCountThreshold);

    double numFeatures = featureCounts.getL2Norm();
    numFeatures *= numFeatures;
    System.out.println(numFeatures + " features with count >= " + featureCountThreshold);

    ListSufficientStatistics featureCountsList = featureCounts.coerceToList();
    int lexiconFeaturesIndex = featureCountsList.getStatisticNames().getIndex(ParametricCcgParser.LEXICON_PARAMETERS);
    featureCounts.coerceToList().getStatistics().set(lexiconFeaturesIndex, null);
    int unaryRuleFeaturesIndex = featureCountsList.getStatisticNames().getIndex(ParametricCcgParser.UNARY_RULE_PARAMETERS);
    featureCounts.coerceToList().getStatistics().set(unaryRuleFeaturesIndex, null);

    return family.rescaleFeatures(featureCounts);
  }

  private static Map<SyntacticCategory, HeadedSyntacticCategory> readSyntaxMap(String filename) {
    Map<SyntacticCategory, HeadedSyntacticCategory> catMap = Maps.newHashMap();
    for (String line : IoUtils.readLines(filename)) {
      String[] parts = line.split(" ");
      SyntacticCategory syntax = SyntacticCategory.parseFrom(parts[0]).getCanonicalForm();
      HeadedSyntacticCategory headedSyntax = HeadedSyntacticCategory.parseFrom(parts[1])
          .getCanonicalForm();
      catMap.put(syntax, headedSyntax);
    }
    return catMap;
  }

  public static void main(String[] args) {
    new TrainCcg().run(args);
  }

  private static class SupertaggerMapper extends Mapper<CcgExample, CcgExample> {

    private final Supertagger supertagger;
    private final double multitagThreshold;
    private final boolean includeGoldSupertags;
    
    public SupertaggerMapper(Supertagger supertagger, double multitagThreshold,
        boolean includeGoldSupertags) {
      this.supertagger = Preconditions.checkNotNull(supertagger);
      this.multitagThreshold = multitagThreshold;
      this.includeGoldSupertags = includeGoldSupertags;
    }

    @Override
    public CcgExample map(CcgExample item) {
      SupertaggedSentence taggedSentence = supertagger.multitag(item.getSentence().getItems(), multitagThreshold);

      if (includeGoldSupertags) {
        // Make sure the correct headed syntactic category for each
        // word is included in the set of candidate syntactic
        // categories.
        List<List<HeadedSyntacticCategory>> predictedLabels = taggedSentence.getLabels();
        List<List<Double>> predictedProbs = taggedSentence.getLabelProbabilities();

        List<HeadedSyntacticCategory> goldSupertags = item.getSyntacticParse().getAllSpannedHeadedSyntacticCategories();
        List<List<HeadedSyntacticCategory>> newLabels = Lists.newArrayList();
        List<List<Double>> newProbs = Lists.newArrayList();
        for (int i = 0; i < predictedLabels.size(); i++) {
          List<HeadedSyntacticCategory> labels = predictedLabels.get(i);
          List<Double> probs = predictedProbs.get(i);
          HeadedSyntacticCategory goldSupertag = goldSupertags.get(i);

          if (goldSupertag == null || labels.contains(goldSupertag)) {
            newLabels.add(labels);
            newProbs.add(probs);
          } else {
            List<HeadedSyntacticCategory> labelsCopy = Lists.newArrayList(labels);
            List<Double> probsCopy = Lists.newArrayList(probs);

            // The probability of the added label has been arbitrarily
            // set to 0. These probabilities should not be used during
            // training.
            labelsCopy.add(goldSupertag);
            probsCopy.add(0.0);
            newLabels.add(labelsCopy);
            newProbs.add(probsCopy);
          }
        }
        
        taggedSentence = taggedSentence.replaceSupertags(newLabels, newProbs);
      }

      return new CcgExample(taggedSentence, item.getDependencies(), item.getSyntacticParse(),
          item.getLogicalForm());
    }
  }
}
