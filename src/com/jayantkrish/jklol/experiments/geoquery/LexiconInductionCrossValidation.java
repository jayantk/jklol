package com.jayantkrish.jklol.experiments.geoquery;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.CcgBeamSearchInference;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.CcgFeatureFactory;
import com.jayantkrish.jklol.ccg.CcgLoglikelihoodOracle;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.ccg.cli.AlignmentLexiconInduction;
import com.jayantkrish.jklol.ccg.lambda2.CommutativeReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionComparator;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.lambda2.LambdaApplicationReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.SimplificationComparator;
import com.jayantkrish.jklol.ccg.lambda2.VariableCanonicalizationReplacementRule;
import com.jayantkrish.jklol.ccg.lexicon.SpanFeatureAnnotation;
import com.jayantkrish.jklol.ccg.lexicon.StringContext;
import com.jayantkrish.jklol.ccg.lexinduct.AlignedExpressionTree;
import com.jayantkrish.jklol.ccg.lexinduct.AlignmentExample;
import com.jayantkrish.jklol.ccg.lexinduct.CfgAlignmentEmOracle;
import com.jayantkrish.jklol.ccg.lexinduct.CfgAlignmentModel;
import com.jayantkrish.jklol.ccg.lexinduct.LagrangianAlignmentDecoder;
import com.jayantkrish.jklol.ccg.lexinduct.LagrangianAlignmentDecoder.LagrangianDecodingResult;
import com.jayantkrish.jklol.ccg.lexinduct.LagrangianAlignmentTrainer;
import com.jayantkrish.jklol.ccg.lexinduct.LagrangianAlignmentTrainer.ParametersAndLagrangeMultipliers;
import com.jayantkrish.jklol.ccg.lexinduct.ParametricCfgAlignmentModel;
import com.jayantkrish.jklol.ccg.util.SemanticParserExampleLoss;
import com.jayantkrish.jklol.ccg.util.SemanticParserUtils;
import com.jayantkrish.jklol.ccg.util.SemanticParserUtils.SemanticParserLoss;
import com.jayantkrish.jklol.cfg.CfgParseTree;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.preprocessing.DictionaryFeatureVectorGenerator;
import com.jayantkrish.jklol.preprocessing.FeatureGenerator;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.training.DefaultLogFunction;
import com.jayantkrish.jklol.training.ExpectationMaximization;
import com.jayantkrish.jklol.training.GradientOptimizer;
import com.jayantkrish.jklol.training.GradientOracle;
import com.jayantkrish.jklol.training.StochasticGradientTrainer;
import com.jayantkrish.jklol.util.CountAccumulator;
import com.jayantkrish.jklol.util.IoUtils;
import com.jayantkrish.jklol.util.PairCountAccumulator;

public class LexiconInductionCrossValidation extends AbstractCli {

  private OptionSpec<String> trainingDataFolds;
  private OptionSpec<String> outputDir;
  
  private OptionSpec<String> foldNameOpt;
  private OptionSpec<Void> testOpt;
  
  private OptionSpec<Integer> unknownWordThreshold;
  
  // Configuration for the alignment model
  private OptionSpec<Integer> emIterations;
  private OptionSpec<Double> smoothingParam;
  private OptionSpec<Integer> nGramLength;
  
  // Configuration for the semantic parser.
  private OptionSpec<Integer> parserIterations;
  private OptionSpec<Integer> beamSize;
  private OptionSpec<Double> l2Regularization;
  private OptionSpec<String> additionalLexicon;

  // TODO: this shouldn't be hard coded. Replace with 
  // an input unification lattice for types.
  private static final Map<String, String> typeReplacements = Maps.newHashMap();
  static {
    typeReplacements.put("lo", "e");
    typeReplacements.put("c", "e");
    typeReplacements.put("co", "e");
    typeReplacements.put("s", "e");
    typeReplacements.put("r", "e");
    typeReplacements.put("l", "e");
    typeReplacements.put("m", "e");
    typeReplacements.put("p", "e");
  }
  
  private static final String FEATURE_ANNOTATION_NAME = "features"; 
  
  public LexiconInductionCrossValidation() {
    super(CommonOptions.MAP_REDUCE);
  }

  @Override
  public void initializeOptions(OptionParser parser) {
    // Required arguments.
    trainingDataFolds = parser.accepts("trainingDataFolds").withRequiredArg().ofType(String.class).required();
    outputDir = parser.accepts("outputDir").withRequiredArg().ofType(String.class).required();
    
    // Optional option to only run one fold
    foldNameOpt = parser.accepts("foldName").withRequiredArg().ofType(String.class);
    testOpt = parser.accepts("test");
    
    // Word count below which the word is mapped to the "unknown" symbol.
    unknownWordThreshold = parser.accepts("unknownWordThreshold").withRequiredArg()
        .ofType(Integer.class).defaultsTo(0);
    
    // Optional arguments
    emIterations = parser.accepts("emIterations").withRequiredArg().ofType(Integer.class).defaultsTo(10);
    smoothingParam = parser.accepts("smoothing").withRequiredArg().ofType(Double.class).defaultsTo(0.01);
    nGramLength = parser.accepts("nGramLength").withRequiredArg().ofType(Integer.class).defaultsTo(1);
    
    parserIterations = parser.accepts("parserIterations").withRequiredArg().ofType(Integer.class).defaultsTo(10);
    beamSize = parser.accepts("beamSize").withRequiredArg().ofType(Integer.class).defaultsTo(100);
    l2Regularization = parser.accepts("l2Regularization").withRequiredArg().ofType(Double.class).defaultsTo(0.0);
    additionalLexicon = parser.accepts("additionalLexicon").withRequiredArg().ofType(String.class);
  }

  @Override
  public void run(OptionSet options) {
    List<String> foldNames = Lists.newArrayList();
    List<List<AlignmentExample>> folds = Lists.newArrayList();
    readFolds(options.valueOf(trainingDataFolds), foldNames, folds, options.has(testOpt));
    
    List<String> additionalLexiconEntries = IoUtils.readLines(options.valueOf(additionalLexicon));

    List<String> foldsToRun = Lists.newArrayList();
    if (options.has(foldNameOpt)) {
      foldsToRun.add(options.valueOf(foldNameOpt));
    } else {
      foldsToRun.addAll(foldNames);
    }

    List<SemanticParserLoss> losses = Lists.newArrayList();
    for (String foldName : foldsToRun) {
      int foldIndex = foldNames.indexOf(foldName);

      List<AlignmentExample> heldOut = folds.get(foldIndex);
      List<AlignmentExample> trainingData = Lists.newArrayList();
      for (int j = 0; j < folds.size(); j++) {
        if (j == foldIndex) {
          continue;
        }
        trainingData.addAll(folds.get(j));
      }

      String outputDirString = options.valueOf(outputDir);
      String lexiconOutputFilename = outputDirString + "/lexicon." + foldName + ".txt";
      String alignmentModelOutputFilename = outputDirString + "/alignment." + foldName + ".ser";
      String parserModelOutputFilename = outputDirString + "/parser." + foldName + ".ser";

      String trainingErrorOutputFilename = outputDirString + "/training_error." + foldName + ".json";
      String testErrorOutputFilename = outputDirString + "/test_error." + foldName + ".json";

      SemanticParserLoss loss = runFold(trainingData, heldOut, options.valueOf(emIterations),
          options.valueOf(smoothingParam), options.valueOf(nGramLength), options.valueOf(parserIterations),
          options.valueOf(l2Regularization), options.valueOf(beamSize),
          options.valueOf(unknownWordThreshold), additionalLexiconEntries, 
          lexiconOutputFilename, trainingErrorOutputFilename, testErrorOutputFilename,
          alignmentModelOutputFilename, parserModelOutputFilename);
      losses.add(loss);
    }
    
    SemanticParserLoss overall = new SemanticParserLoss(0, 0, 0, 0);
    for (int i = 0; i < losses.size(); i++) {
      System.out.println(foldNames.get(i));
      System.out.println("PRECISION: " + losses.get(i).getPrecision());
      System.out.println("RECALL: " + losses.get(i).getRecall());
      System.out.println("LEXICON RECALL: " + losses.get(i).getLexiconRecall());
      overall = overall.add(losses.get(i));
    }
    
    System.out.println("== Overall ==");
    System.out.println("PRECISION: " + overall.getPrecision());
    System.out.println("RECALL: " + overall.getRecall());
    System.out.println("LEXICON RECALL: " + overall.getLexiconRecall());
  }
  
  private static SemanticParserLoss runFold(List<AlignmentExample> trainingData, List<AlignmentExample> testData,
      int emIterations, double smoothingAmount, int nGramLength, int parserIterations, double l2Regularization, int beamSize,
      int unknownWordThreshold, List<String> additionalLexiconEntries, String lexiconOutputFilename,
      String trainingErrorOutputFilename, String testErrorOutputFilename, String alignmentModelOutputFilename,
      String parserModelOutputFilename) {

    // Find all entity names in the given lexicon entries
    Set<List<String>> entityNames = Sets.newHashSet();
    for (LexiconEntry lexiconEntry : LexiconEntry.parseLexiconEntries(additionalLexiconEntries)) {
      entityNames.add(lexiconEntry.getWords());
    }
    
    // Train the alignment model and generate lexicon entries.
    PairCountAccumulator<List<String>, LexiconEntry> alignments = trainAlignmentModel(trainingData,
        entityNames, smoothingAmount, emIterations, nGramLength, false, false);
    
    CountAccumulator<String> wordCounts = CountAccumulator.create();
    for (AlignmentExample trainingExample : trainingData) {
      wordCounts.incrementByOne(trainingExample.getWords());
    }

    // Log the generated lexicon and model.
    Collection<LexiconEntry> allEntries = alignments.getKeyValueMultimap().values();
    List<String> lexiconEntryLines = Lists.newArrayList();
    List<String> unknownLexiconEntryLines = Lists.newArrayList();
    lexiconEntryLines.addAll(additionalLexiconEntries);
    for (LexiconEntry lexiconEntry : allEntries) {
      Expression2 lf = lexiconEntry.getCategory().getLogicalForm();
      if (lf.isConstant()) {
        // Edit the semantics of entities
        String type = lf.getConstant().split(":")[1];
        String newHead = "entity:" + type;
        String oldHeadString = "\"0 " + lexiconEntry.getCategory().getSemanticHeads().get(0) + "\"";
        String newHeadString = "\"0 " + newHead + "\",\"0 " + lf.getConstant() + "\"";
        String lexString = lexiconEntry.toCsvString().replace(oldHeadString, newHeadString);
        lexiconEntryLines.add(lexString);
      } else if (lexiconEntry.getWords().size() == 1 &&
          wordCounts.getCount(lexiconEntry.getWords().get(0)) <= unknownWordThreshold) {
        // Note that all generated entries have only one word.

        String lexString = lexiconEntry.toCsvString();
        lexString = lexString.replace("\"" + lexiconEntry.getWords().get(0) + "\"", "\"" + ParametricCcgParser.DEFAULT_POS_TAG + "\"");
        lexString = lexString.replace(lexiconEntry.getWords().get(0) + "#", ParametricCcgParser.DEFAULT_POS_TAG + "#");
        unknownLexiconEntryLines.add(lexString);
      } else {
        lexiconEntryLines.add(lexiconEntry.toCsvString());
      }
    }

    List<String> allLexiconEntries = Lists.newArrayList(lexiconEntryLines);
    allLexiconEntries.addAll(unknownLexiconEntryLines);
    Collections.sort(allLexiconEntries);
    IoUtils.writeLines(lexiconOutputFilename, allLexiconEntries);
    // IoUtils.serializeObjectToFile(model, alignmentModelOutputFilename);
    
    // Initialize CCG parser components.
    List<CcgExample> ccgTrainingExamples = alignmentExamplesToCcgExamples(trainingData);
    List<String> ruleEntries = Arrays.asList("\"DUMMY{0} DUMMY{0}\",\"(lambda $L $L)\"");

    FeatureVectorGenerator<StringContext> featureGen = getCcgFeatureFactory(ccgTrainingExamples);
    ccgTrainingExamples = featurizeExamples(ccgTrainingExamples, featureGen);
    CcgFeatureFactory featureFactory = new GeoqueryFeatureFactory(true, true,
        FEATURE_ANNOTATION_NAME, featureGen.getFeatureDictionary());

    ExpressionSimplifier simplifier = new ExpressionSimplifier(Arrays.
        <ExpressionReplacementRule>asList(new LambdaApplicationReplacementRule(),
            new VariableCanonicalizationReplacementRule(),
            new CommutativeReplacementRule("and:<t*,t>")));
    ExpressionComparator comparator = new SimplificationComparator(simplifier);

    CcgBeamSearchInference inferenceAlgorithm = new CcgBeamSearchInference(null, comparator, beamSize,
        -1, Integer.MAX_VALUE, Runtime.getRuntime().availableProcessors(), false);

    CcgParser ccgParser = trainSemanticParser(ccgTrainingExamples, lexiconEntryLines,
        unknownLexiconEntryLines, ruleEntries, featureFactory, inferenceAlgorithm, comparator,
        parserIterations, l2Regularization);

    IoUtils.serializeObjectToFile(ccgParser, parserModelOutputFilename);
    
    List<SemanticParserExampleLoss> trainingExampleLosses = Lists.newArrayList();    
    SemanticParserUtils.testSemanticParser(ccgTrainingExamples, ccgParser,
        inferenceAlgorithm, simplifier, comparator, trainingExampleLosses);
    SemanticParserExampleLoss.writeJsonToFile(trainingErrorOutputFilename, trainingExampleLosses);

    List<CcgExample> ccgTestExamples = alignmentExamplesToCcgExamples(testData);
    ccgTestExamples = featurizeExamples(ccgTestExamples, featureGen);
    List<SemanticParserExampleLoss> testExampleLosses = Lists.newArrayList();    
    SemanticParserLoss testLoss = SemanticParserUtils.testSemanticParser(ccgTestExamples, ccgParser,
        inferenceAlgorithm, simplifier, comparator, testExampleLosses);
    SemanticParserExampleLoss.writeJsonToFile(testErrorOutputFilename, testExampleLosses);

    return testLoss;
  }

  public static PairCountAccumulator<List<String>, LexiconEntry> trainAlignmentModel(
      List<AlignmentExample> trainingData, Set<List<String>> entityNames, double smoothingAmount,
      int emIterations, int nGramLength, boolean useLagrangianRelaxation, boolean discriminative) {
    // Preprocess data to generate features.
    FeatureVectorGenerator<Expression2> vectorGenerator = AlignmentLexiconInduction
        .buildFeatureVectorGenerator(trainingData, Collections.<String>emptyList());
    trainingData = AlignmentLexiconInduction.applyFeatureVectorGenerator(vectorGenerator, trainingData);

    // Add all unigrams to the model.
    Set<List<String>> terminalVarValues = Sets.newHashSet();
    for (AlignmentExample example : trainingData) {
      terminalVarValues.addAll(example.getNGrams(1));
    }
    
    // Add any entity names that appear in the training set.
    Set<List<String>> attestedEntityNames = Sets.newHashSet();
    for (AlignmentExample example : trainingData) {
      attestedEntityNames.addAll(example.getNGrams(example.getWords().size()));
    }
    attestedEntityNames.retainAll(entityNames);
    terminalVarValues.addAll(attestedEntityNames);

    ParametricCfgAlignmentModel pam = ParametricCfgAlignmentModel.buildAlignmentModel(
        trainingData, vectorGenerator, terminalVarValues, discriminative, false);
    SufficientStatistics smoothing = pam.getNewSufficientStatistics();
    smoothing.increment(smoothingAmount);

    SufficientStatistics initial = pam.getNewSufficientStatistics();
    initial.increment(1);

    // Train the alignment model with EM.
    if (!useLagrangianRelaxation) {
      ExpectationMaximization em = new ExpectationMaximization(emIterations, new DefaultLogFunction(1, false));
      SufficientStatistics trainedParameters = em.train(new CfgAlignmentEmOracle(pam, smoothing),
          initial, trainingData);

      CfgAlignmentModel model = pam.getModelFromParameters(trainedParameters);

      return AlignmentLexiconInduction.generateLexiconFromAlignmentModel(model, trainingData, typeReplacements);
    } else {
      DiscreteFactor lexiconFactor = TableFactor.unity(pam.getNonterminalVar().union(pam.getTerminalVar()))
          .product(Math.log(0.01));
      LagrangianAlignmentTrainer trainer = new LagrangianAlignmentTrainer(emIterations,
          new LagrangianAlignmentDecoder(20));
      ParametersAndLagrangeMultipliers trainedParameters = trainer.train(pam, initial, smoothing,
          trainingData, lexiconFactor);

      // System.out.println(pam.getParameterDescription(trainedParameters));

      // Get the trained model.
      CfgAlignmentModel model = pam.getModelFromParameters(trainedParameters.getParameters());
      LagrangianDecodingResult result = trainedParameters.getLagrangeMultipliers();

      /*
      // EM trained model.
      ExpectationMaximization em = new ExpectationMaximization(emIterations, new DefaultLogFunction(1, false));
      SufficientStatistics trainedParameters = em.train(new CfgAlignmentEmOracle(pam, smoothing),
          initial, trainingData);

      CfgAlignmentModel model = pam.getModelFromParameters(trainedParameters);
      
      LagrangianAlignmentDecoder decoder = new LagrangianAlignmentDecoder(1000);
      DiscreteFactor lexiconFactor = TableFactor.unity(model.getParentVar().union(model.getTerminalVar()))
          .product(Math.log(0.01));
      VariableNumMap nonterminalVar = model.getParentVar();
      DiscreteFactor skipIndicatorFactor = TableFactor.pointDistribution(nonterminalVar,
          nonterminalVar.outcomeArrayToAssignment(ParametricCfgAlignmentModel.SKIP_EXPRESSION)); 
      lexiconFactor = lexiconFactor.product(TableFactor.unity(nonterminalVar).add(skipIndicatorFactor.product(-1.0)));

      LagrangianDecodingResult result = decoder.decode(model, trainingData, lexiconFactor);
       */

      PairCountAccumulator<List<String>, LexiconEntry> alignments = PairCountAccumulator.create();
      for (int i = 0; i < trainingData.size(); i++) {
        CfgParseTree parse = result.getParseTrees().get(i);
        AlignedExpressionTree tree = model.decodeCfgParse(parse);

        for (LexiconEntry entry : tree.generateLexiconEntries(typeReplacements)) {
          alignments.incrementOutcome(entry.getWords(), entry, 1);
        }
      }

      return alignments;
    }
  }

  public static CcgParser trainSemanticParser(List<CcgExample> trainingExamples,
      List<String> lexiconEntryLines, List<String> unknownLexiconEntryLines,
      List<String> ruleEntries, CcgFeatureFactory featureFactory,
      CcgBeamSearchInference inferenceAlgorithm, ExpressionComparator comparator,
      int iterations, double l2Penalty) {
    ParametricCcgParser family = ParametricCcgParser.parseFromLexicon(lexiconEntryLines,
        unknownLexiconEntryLines, ruleEntries, featureFactory, null, true, null, true);

    /*
    GradientOracle<CcgParser, CcgExample> oracle = new CcgPerceptronOracle(family,
        inferenceAlgorithm, 0.0);
        */
    GradientOracle<CcgParser, CcgExample> oracle = new CcgLoglikelihoodOracle(family, comparator, inferenceAlgorithm);

    int numIterations = trainingExamples.size() * iterations;
    GradientOptimizer trainer = StochasticGradientTrainer.createWithL2Regularization(numIterations, 1,
        1.0, true, true, l2Penalty, new DefaultLogFunction(100, false));
    SufficientStatistics parameters = trainer.train(oracle, oracle.initializeGradient(),
        trainingExamples);

    /*
    GradientOptimizer sgdTrainer = StochasticGradientTrainer.createWithL2Regularization(
        trainingExamples.size(), 1, 1.0, true, true, l2Penalty, new DefaultLogFunction(100, false));
    SufficientStatistics sgdParameters = sgdTrainer.train(oracle, oracle.initializeGradient(),
        trainingExamples);

    SufficientStatistics parameters = null;
    try {
      GradientOptimizer trainer = new Lbfgs(iterations, 50, l2Penalty, 1e-4,
          0.005, new DefaultLogFunction(1, false));
      parameters = trainer.train(oracle, sgdParameters, trainingExamples);
    } catch (LbfgsConvergenceError e) {
      parameters = e.getFinalParameters();
    }
    */

    return family.getModelFromParameters(parameters);
  }

  public static List<CcgExample> alignmentExamplesToCcgExamples(
      List<AlignmentExample> alignmentExamples) {
    // Convert data to CCG training data.
    List<CcgExample> ccgExamples = Lists.newArrayList();
    for (AlignmentExample example : alignmentExamples) {
      List<String> words = example.getWords();
      List<String> posTags = Collections.nCopies(words.size(), ParametricCcgParser.DEFAULT_POS_TAG);
      AnnotatedSentence supertaggedSentence = new AnnotatedSentence(words, posTags);

      ccgExamples.add(new CcgExample(supertaggedSentence, null, null,
          example.getTree().getExpression()));
    }
    return ccgExamples;
  }
  
  private static FeatureVectorGenerator<StringContext> getCcgFeatureFactory(List<CcgExample> examples) {
    List<StringContext> contexts = StringContext.getContextsFromExamples(examples);
    FeatureGenerator<StringContext, String> featureGen = new GeoqueryFeatureGenerator();
    return DictionaryFeatureVectorGenerator.createFromData(contexts, featureGen, true);
  }

  private static List<CcgExample> featurizeExamples(List<CcgExample> examples,
      FeatureVectorGenerator<StringContext> featureGen) {
    List<CcgExample> newExamples = Lists.newArrayList();
    for (CcgExample example : examples) {
      AnnotatedSentence sentence = example.getSentence();
      SpanFeatureAnnotation annotation = SpanFeatureAnnotation.annotate(sentence, featureGen);
      
      AnnotatedSentence annotatedSentence = sentence.addAnnotation(FEATURE_ANNOTATION_NAME, annotation);
      
      newExamples.add(new CcgExample(annotatedSentence, example.getDependencies(),
          example.getSyntacticParse(), example.getLogicalForm()));
    }
    return newExamples;
  }

  public static void readFolds(String foldDir, List<String> foldNames, List<List<AlignmentExample>> folds,
      boolean test) {
    File dir = new File(foldDir);
    File[] files = dir.listFiles();
    
    for (int i = 0; i < files.length; i++) {
      String name = files[i].getName();
      if (!test && name.startsWith("fold")) {
        foldNames.add(name);
        
        List<AlignmentExample> foldData = AlignmentLexiconInduction
            .readTrainingData(files[i].getAbsolutePath());
        folds.add(foldData);
      } else if (test && (name.startsWith("all_folds") || name.startsWith("test"))) {
        foldNames.add(name);
        
        List<AlignmentExample> foldData = AlignmentLexiconInduction
            .readTrainingData(files[i].getAbsolutePath());
        folds.add(foldData);
      }
    }
  }

  public static void main(String[] args) {
    new LexiconInductionCrossValidation().run(args);
  }
}
