package com.jayantkrish.jklol.experiments.geoquery;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.jayantkrish.jklol.ccg.CcgBeamSearchInference;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.CcgFeatureFactory;
import com.jayantkrish.jklol.ccg.CcgInference;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.CcgPerceptronOracle;
import com.jayantkrish.jklol.ccg.DefaultCcgFeatureFactory;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.ccg.cli.AlignmentLexiconInduction;
import com.jayantkrish.jklol.ccg.lambda2.ConjunctionReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionComparator;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.lambda2.LambdaApplicationReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.SimplificationComparator;
import com.jayantkrish.jklol.ccg.lambda2.VariableCanonicalizationReplacementRule;
import com.jayantkrish.jklol.ccg.lexinduct.AlignedExpressionTree;
import com.jayantkrish.jklol.ccg.lexinduct.AlignedExpressionTree.AlignedExpression;
import com.jayantkrish.jklol.ccg.lexinduct.AlignmentExample;
import com.jayantkrish.jklol.ccg.lexinduct.CfgAlignmentEmOracle;
import com.jayantkrish.jklol.ccg.lexinduct.CfgAlignmentModel;
import com.jayantkrish.jklol.ccg.lexinduct.ParametricCfgAlignmentModel;
import com.jayantkrish.jklol.ccg.supertag.ListSupertaggedSentence;
import com.jayantkrish.jklol.ccg.supertag.SupertaggedSentence;
import com.jayantkrish.jklol.ccg.util.SemanticParserUtils;
import com.jayantkrish.jklol.ccg.util.SemanticParserUtils.SemanticParserLoss;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.parallel.Mapper;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.training.DefaultLogFunction;
import com.jayantkrish.jklol.training.ExpectationMaximization;
import com.jayantkrish.jklol.training.GradientOptimizer;
import com.jayantkrish.jklol.training.GradientOracle;
import com.jayantkrish.jklol.training.StochasticGradientTrainer;
import com.jayantkrish.jklol.util.IoUtils;
import com.jayantkrish.jklol.util.PairCountAccumulator;

public class LexiconInductionCrossValidation extends AbstractCli {

  private OptionSpec<String> trainingDataFolds;
  private OptionSpec<String> outputDir;
  
  private OptionSpec<String> foldNameOpt;
  
  // Configuration for the alignment model
  private OptionSpec<Integer> emIterations;
  private OptionSpec<Double> smoothingParam;
  
  // Configuration for the semantic parser.
  private OptionSpec<Integer> sgdIterations;
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
    
    // Optional arguments
    emIterations = parser.accepts("emIterations").withRequiredArg().ofType(Integer.class).defaultsTo(10);
    smoothingParam = parser.accepts("smoothing").withRequiredArg().ofType(Double.class).defaultsTo(0.01);
    
    sgdIterations = parser.accepts("sgdIterations").withRequiredArg().ofType(Integer.class).defaultsTo(10);
    beamSize = parser.accepts("beamSize").withRequiredArg().ofType(Integer.class).defaultsTo(100);
    l2Regularization = parser.accepts("l2Regularization").withRequiredArg().ofType(Double.class).defaultsTo(0.0);
    additionalLexicon = parser.accepts("additionalLexicon").withRequiredArg().ofType(String.class);
  }

  @Override
  public void run(OptionSet options) {
    List<String> foldNames = Lists.newArrayList();
    List<List<AlignmentExample>> folds = Lists.newArrayList();
    readFolds(options.valueOf(trainingDataFolds), foldNames, folds);
    
    List<String> additionalLexiconEntries = IoUtils.readLines(options.valueOf(additionalLexicon));
    
    FoldRunner runner = new FoldRunner(foldNames, folds, options.valueOf(emIterations),
        options.valueOf(smoothingParam), options.valueOf(sgdIterations),
        options.valueOf(l2Regularization), options.valueOf(beamSize), additionalLexiconEntries,
        options.valueOf(outputDir));
    
    // MapReduceExecutor executor = new LocalMapReduceExecutor(1, 1);
    List<SemanticParserLoss> losses = Lists.newArrayList();
    if (options.has(foldNameOpt)) {
      losses.add(runner.apply(options.valueOf(foldNameOpt)));
    } else {
      for (String foldName : foldNames) {
        losses.add(runner.apply(foldName));
      }
    }
    
    SemanticParserLoss overall = new SemanticParserLoss(0, 0, 0);
    for (int i = 0; i < losses.size(); i++) {
      System.out.println(foldNames.get(i));
      System.out.println("PRECISION: " + losses.get(i).getPrecision());
      System.out.println("RECALL: " + losses.get(i).getRecall());
      
      overall = overall.add(losses.get(i));
    }
    
    System.out.println("== Overall ==");
    System.out.println("PRECISION: " + overall.getPrecision());
    System.out.println("RECALL: " + overall.getRecall());
  }
  
  private static SemanticParserLoss runFold(List<AlignmentExample> trainingData, List<AlignmentExample> testData,
      int emIterations, double smoothingAmount, int sgdIterations, double l2Regularization, int beamSize,
      List<String> additionalLexiconEntries, String lexiconOutputFilename, String alignmentModelOutputFilename,
      String parserModelOutputFilename) {
    
    // Train the alignment model.
    CfgAlignmentModel model = trainAlignmentModel(trainingData, smoothingAmount, emIterations);
    
    // Generate lexicon entries from the training data using the
    // alignment model's predictions.
    PairCountAccumulator<List<String>, LexiconEntry> alignments = AlignmentLexiconInduction
        .generateLexiconFromAlignmentModel(model, trainingData, typeReplacements);

    // Log the generated lexicon and model.
    Collection<LexiconEntry> allEntries = alignments.getKeyValueMultimap().values();  
    List<String> lexiconEntryLines = Lists.newArrayList();
    lexiconEntryLines.addAll(additionalLexiconEntries);
    for (LexiconEntry lexiconEntry : allEntries) {
      lexiconEntryLines.add(lexiconEntry.toCsvString());
    }
    Collections.sort(lexiconEntryLines);
    IoUtils.writeLines(lexiconOutputFilename, lexiconEntryLines);
    IoUtils.serializeObjectToFile(model, alignmentModelOutputFilename);
    
    // Initialize CCG parser components.
    List<CcgExample> ccgTrainingExamples = alignmentExamplesToCcgExamples(trainingData, model);
    List<String> ruleEntries = Arrays.asList("\"DUMMY{0} DUMMY{0}\",\"(lambda $L $L)\"");
    CcgFeatureFactory featureFactory = new DefaultCcgFeatureFactory(null, false);

    ExpressionSimplifier simplifier = new ExpressionSimplifier(Arrays.
        <ExpressionReplacementRule>asList(new LambdaApplicationReplacementRule(),
            new VariableCanonicalizationReplacementRule(),
            new ConjunctionReplacementRule("and:<t*,t>")));
    ExpressionComparator comparator = new SimplificationComparator(simplifier);

    CcgInference inferenceAlgorithm = new CcgBeamSearchInference(null, comparator, beamSize,
        -1, Integer.MAX_VALUE, Runtime.getRuntime().availableProcessors(), false);
    
    CcgParser ccgParser = trainSemanticParser(ccgTrainingExamples, lexiconEntryLines, ruleEntries,
        featureFactory, inferenceAlgorithm, sgdIterations, l2Regularization);

    IoUtils.serializeObjectToFile(ccgParser, parserModelOutputFilename);
    
    List<CcgExample> ccgTestExamples = alignmentExamplesToCcgExamples(testData, null);
    
    return SemanticParserUtils.testSemanticParser(ccgTestExamples, ccgParser,
        inferenceAlgorithm, simplifier, comparator);
  }

  private static CfgAlignmentModel trainAlignmentModel(List<AlignmentExample> trainingData,
      double smoothingAmount, int emIterations) {
        // Preprocess data to generate features.
    FeatureVectorGenerator<Expression2> vectorGenerator = AlignmentLexiconInduction
        .buildFeatureVectorGenerator(trainingData, Collections.<String>emptyList());
    System.out.println("features: " + vectorGenerator.getFeatureDictionary().getValues());
    trainingData = AlignmentLexiconInduction.applyFeatureVectorGenerator(vectorGenerator, trainingData);

    // Train the alignment model with EM.
    ParametricCfgAlignmentModel pam = ParametricCfgAlignmentModel.buildAlignmentModel(
        trainingData, vectorGenerator);
    SufficientStatistics smoothing = pam.getNewSufficientStatistics();
    smoothing.increment(smoothingAmount);

    SufficientStatistics initial = pam.getNewSufficientStatistics();
    initial.increment(1);

    ExpectationMaximization em = new ExpectationMaximization(emIterations, new DefaultLogFunction(1, false));
    SufficientStatistics trainedParameters = em.train(new CfgAlignmentEmOracle(pam, smoothing),
        initial, trainingData);

    // Get the trained model.
    return pam.getModelFromParameters(trainedParameters);
  }

  private static CcgParser trainSemanticParser(List<CcgExample> trainingExamples,
      List<String> lexiconEntryLines, List<String> ruleEntries, CcgFeatureFactory featureFactory,
      CcgInference inferenceAlgorithm, int iterations, double l2Penalty) {
    ParametricCcgParser family = ParametricCcgParser.parseFromLexicon(lexiconEntryLines,
        ruleEntries, featureFactory, null, false, null, true, false);

    GradientOracle<CcgParser, CcgExample> oracle = new CcgPerceptronOracle(family,
        inferenceAlgorithm, 0.0);

    int numIterations = trainingExamples.size() * iterations;
    GradientOptimizer trainer = StochasticGradientTrainer.createWithL2Regularization(numIterations, 1,
        1.0, true, true, l2Penalty, new DefaultLogFunction(100, false));
    SufficientStatistics parameters = trainer.train(oracle, oracle.initializeGradient(),
        trainingExamples);
    return family.getModelFromParameters(parameters);
  }

  private static List<CcgExample> alignmentExamplesToCcgExamples(
      List<AlignmentExample> alignmentExamples, CfgAlignmentModel model) {
    // Convert data to CCG training data.
    List<CcgExample> ccgExamples = Lists.newArrayList();
    for (AlignmentExample example : alignmentExamples) {
      List<String> words = example.getWords();
      List<String> posTags = Collections.nCopies(words.size(), ParametricCcgParser.DEFAULT_POS_TAG);
      SupertaggedSentence supertaggedSentence = ListSupertaggedSentence
          .createWithUnobservedSupertags(words, posTags);

      List<Expression2> ccgLexiconEntries = null;
      if (model != null) {
        AlignedExpressionTree tree = model.getBestAlignment(example);
        // TODO: this should be refactored somehow.
        Multimap<String, AlignedExpression> treeAlignments = tree.getWordAlignments();
        ccgLexiconEntries = Lists.newArrayList(Collections.nCopies(
            words.size(), ParametricCcgParser.SKIP_LF));
        for (int j = 0; j < words.size(); j++) {
          for (AlignedExpression alignedExp : treeAlignments.get(words.get(j))) {
            if (alignedExp.getSpanStart() == j && alignedExp.getSpanEnd() == j + 1) {
              ccgLexiconEntries.set(j, alignedExp.getExpression()); 
              System.out.println(j + " " + words.get(j) + " " +  alignedExp.getExpression());
            }
          }
        }
      }

      ccgExamples.add(new CcgExample(supertaggedSentence, null, null,
          example.getTree().getExpression(), ccgLexiconEntries));
    }
    return ccgExamples;
  }
  
  private static void readFolds(String foldDir, List<String> foldNames, List<List<AlignmentExample>> folds) {
    File dir = new File(foldDir);
    File[] files = dir.listFiles();
    
    for (int i = 0; i < files.length; i++) {
      String name = files[i].getName();
      if (name.startsWith("fold")) {
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
  
  private static class FoldRunner extends Mapper<String, SemanticParserLoss> {

    private final List<String> foldNames;
    private final List<List<AlignmentExample>> folds;
    
    private final int emIterations;
    private final double smoothing;
    private final int sgdIterations;
    private final double l2Regularization;
    private final int beamSize;
    private final List<String> additionalLexiconEntries;
    private final String outputDir;
    
    public FoldRunner(List<String> foldNames, List<List<AlignmentExample>> folds,
        int emIterations, double smoothing, int sgdIterations, double l2Regularization,
        int beamSize, List<String> additionalLexiconEntries, String outputDir) {
      this.foldNames = foldNames;
      this.folds = folds;
      
      this.emIterations = emIterations;
      this.smoothing = smoothing;
      this.sgdIterations = sgdIterations;
      this.l2Regularization = l2Regularization;
      this.beamSize = beamSize;
      this.additionalLexiconEntries = additionalLexiconEntries;
      this.outputDir = outputDir;
    }

    @Override
    public SemanticParserLoss map(String item) {
      int foldIndex = foldNames.indexOf(item);
      
      List<AlignmentExample> heldOut = folds.get(foldIndex);
      List<AlignmentExample> trainingData = Lists.newArrayList();
      for (int j = 0; j < folds.size(); j++) {
        if (j == foldIndex) {
          continue;
        }
        trainingData.addAll(folds.get(j));
      }

      String lexiconOutputFilename = outputDir + "/lexicon." + item + ".txt";
      String alignmentModelOutputFilename = outputDir + "/alignment." + item + ".ser";
      String parserModelOutputFilename = outputDir + "/parser." + item + ".ser";
      
      // TODO:
      // System.setOut()
      
      return runFold(trainingData, heldOut, emIterations, smoothing,
          sgdIterations, l2Regularization, beamSize, additionalLexiconEntries,
          lexiconOutputFilename, alignmentModelOutputFilename, parserModelOutputFilename);
    }
  }
}
