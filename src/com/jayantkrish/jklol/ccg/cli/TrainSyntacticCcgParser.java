package com.jayantkrish.jklol.ccg.cli;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.CcgCkyInference;
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
import com.jayantkrish.jklol.ccg.lambda2.ExpressionComparator;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.lambda2.SimplificationComparator;
import com.jayantkrish.jklol.ccg.supertag.ListSupertaggedSentence;
import com.jayantkrish.jklol.ccg.supertag.Supertagger;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.data.DataFormat;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
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
public class TrainSyntacticCcgParser extends AbstractCli {

  private OptionSpec<String> trainingData;
  private OptionSpec<String> modelOutput;

  // CCG parser options
  private OptionSpec<String> ccgLexicon;
  private OptionSpec<String> ccgUnknownLexicon;
  private OptionSpec<String> ccgRules;
  private OptionSpec<Void> ccgApplicationOnly;
  private OptionSpec<Void> ccgNormalFormOnly;

  private OptionSpec<String> syntaxMap;
  private OptionSpec<Integer> beamSize;
  private OptionSpec<Long> maxParseTimeMillis;
  private OptionSpec<Integer> maxChartSize;
  private OptionSpec<Integer> parserThreads;
  private OptionSpec<String> supertagger;
  private OptionSpec<Double> multitagThreshold;
  private OptionSpec<Void> useCcgBankFormat;
  private OptionSpec<Double> maxMargin;
  private OptionSpec<Void> ignoreSemantics;
  private OptionSpec<Void> onlyObservedBinaryRules;
  private OptionSpec<Void> exactInference;
  
  public static final String SUPERTAG_ANNOTATION_NAME = "supertags";

  public TrainSyntacticCcgParser() {
    super(CommonOptions.STOCHASTIC_GRADIENT, CommonOptions.MAP_REDUCE);
  }

  @Override
  public void initializeOptions(OptionParser parser) {
    // Required arguments.
    trainingData = parser.accepts("trainingData").withRequiredArg().ofType(String.class).required();
    modelOutput = parser.accepts("output").withRequiredArg().ofType(String.class).required();
    
    // CCG parser arguments
    ccgLexicon = parser.accepts("lexicon",
        "The CCG lexicon defining the grammar to use.").withRequiredArg().ofType(String.class).required();
    ccgUnknownLexicon = parser.accepts("unknownLexicon",
        "The CCG lexicon for unknown words.").withRequiredArg().ofType(String.class);
    ccgRules = parser.accepts("rules",
        "Binary and unary rules to use during CCG parsing, in addition to function application and composition.")
        .withRequiredArg().ofType(String.class);
    ccgApplicationOnly = parser.accepts("applicationOnly",
        "Use only function application during parsing, i.e., no composition.");
    ccgNormalFormOnly = parser.accepts("normalFormOnly",
        "Only permit CCG derivations in Eisner normal form.");

    // Optional options
    syntaxMap = parser.accepts("syntaxMap").withRequiredArg().ofType(String.class);
    beamSize = parser.accepts("beamSize").withRequiredArg().ofType(Integer.class).defaultsTo(100);
    maxParseTimeMillis = parser.accepts("maxParseTimeMillis").withRequiredArg().ofType(Long.class).defaultsTo(-1L);
    maxChartSize = parser.accepts("maxChartSize").withRequiredArg().ofType(Integer.class).defaultsTo(Integer.MAX_VALUE);
    parserThreads = parser.accepts("parserThreads").withRequiredArg().ofType(Integer.class).defaultsTo(1);
    supertagger = parser.accepts("supertagger").withRequiredArg().ofType(String.class);
    multitagThreshold = parser.accepts("multitagThreshold").withRequiredArg().ofType(Double.class);
    useCcgBankFormat = parser.accepts("useCcgBankFormat");
    maxMargin = parser.accepts("maxMargin").withRequiredArg().ofType(Double.class);
    ignoreSemantics = parser.accepts("ignoreSemantics");
    onlyObservedBinaryRules = parser.accepts("onlyObservedBinaryRules");
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
    CcgFeatureFactory featureFactory = new DefaultCcgFeatureFactory(null, null, true, false,
        SUPERTAG_ANNOTATION_NAME);
    List<String> lexiconEntries = IoUtils.readLines(options.valueOf(ccgLexicon));
    List<String> unknownLexiconEntries = options.has(ccgUnknownLexicon) ?
        IoUtils.readLines(options.valueOf(ccgUnknownLexicon)) : Collections.<String>emptyList();
    List<String> ruleEntries = options.has(ccgRules) ? IoUtils.readLines(options.valueOf(ccgRules))
        : Collections.<String> emptyList();
    ParametricCcgParser family = ParametricCcgParser.parseFromLexicon(lexiconEntries,
        unknownLexiconEntries, ruleEntries, featureFactory, posTags,
        !options.has(ccgApplicationOnly), observedRules, options.has(ccgNormalFormOnly));
    
    System.out.println("Done creating ParametricCcgParser.");

    // Read in training data and confirm its validity.
    CcgParser parser = family.getModelFromParameters(family.getNewSufficientStatistics());

    System.out.println(parser.getSyntaxDistribution().getParameterDescription());

    List<CcgExample> trainingExamples = CcgParserUtils.filterExampleCollection(
        parser, unfilteredTrainingExamples);
    System.out.println(trainingExamples.size() + " training examples.");
    int numDiscarded = unfilteredTrainingExamples.size() - trainingExamples.size();
    System.out.println(numDiscarded + " discarded training examples.");

    if (options.has(logParametersDir)) {
      IoUtils.serializeObjectToFile(family, options.valueOf(logParametersDir) + File.separator + "family.ser");
    }

    // Configure the inference algorithm.
    CcgInference inferenceAlgorithm = new CcgCkyInference(null, options.valueOf(beamSize),
          options.valueOf(maxParseTimeMillis), options.valueOf(maxChartSize),
          options.valueOf(parserThreads));

    // Train the model.
    GradientOracle<CcgParser, CcgExample> oracle = null;
    ExpressionComparator comparator = new SimplificationComparator(ExpressionSimplifier.lambdaCalculus());
    if (options.has(maxMargin)) {
      oracle = new CcgPerceptronOracle(family, comparator, inferenceAlgorithm, options.valueOf(maxMargin));
    } else {
      oracle = new CcgLoglikelihoodOracle(family, comparator, inferenceAlgorithm);
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

  public static List<CcgExample> readTrainingData(String filename,
      boolean ignoreSemantics, boolean useCcgBankFormat, String syntacticCategoryMapFilename) {
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

  public static Map<SyntacticCategory, HeadedSyntacticCategory> readSyntaxMap(String filename) {
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
    new TrainSyntacticCcgParser().run(args);
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
      ListSupertaggedSentence taggedSentence = supertagger.multitag(
          item.getSentence().getWordsAndPosTags(), multitagThreshold);

      if (includeGoldSupertags) {
        // Make sure the correct headed syntactic category for each
        // word is included in the set of candidate syntactic
        // categories.
        List<List<HeadedSyntacticCategory>> predictedLabels = taggedSentence.getSupertags();
        List<List<Double>> predictedProbs = taggedSentence.getSupertagScores();

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
      AnnotatedSentence annotatedSentence = item.getSentence().addAnnotation(
          SUPERTAG_ANNOTATION_NAME, taggedSentence.getAnnotation());

      return new CcgExample(annotatedSentence, item.getDependencies(), item.getSyntacticParse(),
          item.getLogicalForm());
    }
  }
}
