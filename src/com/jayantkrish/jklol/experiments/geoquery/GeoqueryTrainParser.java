package com.jayantkrish.jklol.experiments.geoquery;

import java.util.Arrays;
import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgBeamSearchInference;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.CcgFeatureFactory;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.lambda2.CommutativeReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionComparator;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.lambda2.LambdaApplicationReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.SimplificationComparator;
import com.jayantkrish.jklol.ccg.lambda2.VariableCanonicalizationReplacementRule;
import com.jayantkrish.jklol.ccg.lexicon.StringContext;
import com.jayantkrish.jklol.ccg.lexinduct.AlignmentExample;
import com.jayantkrish.jklol.ccg.util.SemanticParserExampleLoss;
import com.jayantkrish.jklol.ccg.util.SemanticParserUtils;
import com.jayantkrish.jklol.ccg.util.SemanticParserUtils.SemanticParserLoss;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.preprocessing.DictionaryFeatureVectorGenerator;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.util.IoUtils;

public class GeoqueryTrainParser extends AbstractCli {
  
  private OptionSpec<String> trainingDataFolds;
  private OptionSpec<String> outputDir;
  
  private OptionSpec<String> foldNameOpt;
  private OptionSpec<Void> testOpt;

  // Configuration for the semantic parser.
  private OptionSpec<Integer> parserIterations;
  private OptionSpec<Integer> beamSize;
  private OptionSpec<Double> l2Regularization;
  private OptionSpec<String> additionalLexicon;
  
  public GeoqueryTrainParser() {
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
    
    parserIterations = parser.accepts("parserIterations").withRequiredArg().ofType(Integer.class).defaultsTo(10);
    beamSize = parser.accepts("beamSize").withRequiredArg().ofType(Integer.class).defaultsTo(100);
    l2Regularization = parser.accepts("l2Regularization").withRequiredArg().ofType(Double.class).defaultsTo(0.0);
    additionalLexicon = parser.accepts("additionalLexicon").withRequiredArg().ofType(String.class);
  }

  @Override
  public void run(OptionSet options) {
    List<String> foldNames = Lists.newArrayList();
    List<List<AlignmentExample>> folds = Lists.newArrayList();
    GeoqueryInduceLexicon.readFolds(options.valueOf(trainingDataFolds), foldNames, folds, options.has(testOpt));
    
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
      String lexiconInputFilename = outputDirString + "/lexicon." + foldName + ".txt";
      String parserModelOutputFilename = outputDirString + "/parser." + foldName + ".ser";

      String trainingErrorOutputFilename = outputDirString + "/training_error." + foldName + ".json";
      String testErrorOutputFilename = outputDirString + "/test_error." + foldName + ".json";
      
      List<String> lexiconEntryLines = IoUtils.readLines(lexiconInputFilename);
      List<String> unknownLexiconEntryLines = Lists.newArrayList();

      SemanticParserLoss loss = runFold(trainingData, heldOut,
          options.valueOf(parserIterations), options.valueOf(l2Regularization), options.valueOf(beamSize),
          additionalLexiconEntries, lexiconEntryLines, unknownLexiconEntryLines,
          trainingErrorOutputFilename, testErrorOutputFilename,
          parserModelOutputFilename);
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
  
  private SemanticParserLoss runFold(List<AlignmentExample> trainingData, List<AlignmentExample> testData,
      int parserIterations, double l2Regularization, int beamSize,
      List<String> additionalLexiconEntries, List<String> lexiconEntryLines, List<String> unknownLexiconEntryLines,
      String trainingErrorOutputFilename, String testErrorOutputFilename,
      String parserModelOutputFilename) {
    // Initialize CCG parser components.
    List<CcgExample> ccgTrainingExamples = GeoqueryInduceLexicon.alignmentExamplesToCcgExamples(trainingData);
    List<String> ruleEntries = Arrays.asList("\"DUMMY{0} DUMMY{0}\",\"(lambda $L $L)\"");

    // Generate a dictionary of string context features.
    List<StringContext> contexts = StringContext.getContextsFromExamples(ccgTrainingExamples);
    FeatureVectorGenerator<StringContext> featureGen = DictionaryFeatureVectorGenerator
        .createFromData(contexts, new GeoqueryFeatureGenerator(), true);

    ccgTrainingExamples = SemanticParserUtils.annotateFeatures(ccgTrainingExamples, featureGen,
        GeoqueryUtil.FEATURE_ANNOTATION_NAME);
    CcgFeatureFactory featureFactory = new GeoqueryFeatureFactory(true, true,
        GeoqueryUtil.FEATURE_ANNOTATION_NAME, featureGen.getFeatureDictionary(),
        LexiconEntry.parseLexiconEntries(additionalLexiconEntries));

    ExpressionSimplifier simplifier = new ExpressionSimplifier(Arrays.
        <ExpressionReplacementRule>asList(new LambdaApplicationReplacementRule(),
            new VariableCanonicalizationReplacementRule(),
            new CommutativeReplacementRule("and:<t*,t>")));
    ExpressionComparator comparator = new SimplificationComparator(simplifier);

    CcgBeamSearchInference inferenceAlgorithm = new CcgBeamSearchInference(null, comparator, beamSize,
        -1, Integer.MAX_VALUE, Runtime.getRuntime().availableProcessors(), false);

    CcgParser ccgParser = GeoqueryInduceLexicon.trainSemanticParser(ccgTrainingExamples, lexiconEntryLines,
        unknownLexiconEntryLines, ruleEntries, featureFactory, inferenceAlgorithm, comparator,
        parserIterations, l2Regularization);

    IoUtils.serializeObjectToFile(ccgParser, parserModelOutputFilename);
    
    List<SemanticParserExampleLoss> trainingExampleLosses = Lists.newArrayList();    
    SemanticParserUtils.testSemanticParser(ccgTrainingExamples, ccgParser,
        inferenceAlgorithm, simplifier, comparator, trainingExampleLosses);
    SemanticParserExampleLoss.writeJsonToFile(trainingErrorOutputFilename, trainingExampleLosses);

    List<CcgExample> ccgTestExamples = GeoqueryInduceLexicon.alignmentExamplesToCcgExamples(testData);
    ccgTestExamples = SemanticParserUtils.annotateFeatures(ccgTestExamples, featureGen, GeoqueryUtil.FEATURE_ANNOTATION_NAME);
    List<SemanticParserExampleLoss> testExampleLosses = Lists.newArrayList();    
    SemanticParserLoss testLoss = SemanticParserUtils.testSemanticParser(ccgTestExamples, ccgParser,
        inferenceAlgorithm, simplifier, comparator, testExampleLosses);
    SemanticParserExampleLoss.writeJsonToFile(testErrorOutputFilename, testExampleLosses);

    return testLoss;
  }
  
  public static void main(String[] args) {
    new GeoqueryTrainParser().run(args);
  }
}
