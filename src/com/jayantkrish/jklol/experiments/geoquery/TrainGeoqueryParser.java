package com.jayantkrish.jklol.experiments.geoquery;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgCkyInference;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.CcgFeatureFactory;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.cli.TrainSemanticParser;
import com.jayantkrish.jklol.ccg.lambda2.CommutativeReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionComparator;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.lambda2.LambdaApplicationReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.SimplificationComparator;
import com.jayantkrish.jklol.ccg.lambda2.VariableCanonicalizationReplacementRule;
import com.jayantkrish.jklol.ccg.lexicon.StringContext;
import com.jayantkrish.jklol.ccg.util.SemanticParserExampleLoss;
import com.jayantkrish.jklol.ccg.util.SemanticParserUtils;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.preprocessing.DictionaryFeatureVectorGenerator;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.util.IoUtils;
import com.jayantkrish.jklol.util.Pseudorandom;

public class TrainGeoqueryParser extends AbstractCli {
  
  private OptionSpec<String> trainingData;
  private OptionSpec<String> testData;
  private OptionSpec<String> lexicon;
  private OptionSpec<String> npLexicon;

  private OptionSpec<String> outputDir;
  private OptionSpec<String> foldName;

  private OptionSpec<Integer> parserIterations;
  private OptionSpec<Integer> beamSize;
  private OptionSpec<Double> l2Regularization;

  public TrainGeoqueryParser() {
    super(CommonOptions.MAP_REDUCE);
  }

  @Override
  public void initializeOptions(OptionParser parser) {
    // Required arguments.
    trainingData = parser.accepts("trainingData").withRequiredArg().ofType(String.class).withValuesSeparatedBy(',').required();
    outputDir = parser.accepts("outputDir").withRequiredArg().ofType(String.class).required();
    foldName = parser.accepts("foldName").withRequiredArg().ofType(String.class).required();
    lexicon = parser.accepts("lexicon").withRequiredArg().ofType(String.class).required();
    npLexicon = parser.accepts("npLexicon").withRequiredArg().ofType(String.class).required();

    // If given, evaluates the parser on held out data.
    testData = parser.accepts("testData").withRequiredArg().ofType(String.class).withValuesSeparatedBy(',');
    
    // Parser configuration.
    parserIterations = parser.accepts("parserIterations").withRequiredArg().ofType(Integer.class).defaultsTo(10);
    beamSize = parser.accepts("beamSize").withRequiredArg().ofType(Integer.class).defaultsTo(100);
    l2Regularization = parser.accepts("l2Regularization").withRequiredArg().ofType(Double.class).defaultsTo(0.0);
  }

  @Override
  public void run(OptionSet options) {
    // Read training and test data.
    List<CcgExample> trainingExamples = Lists.newArrayList();
    for (String dataFile : options.valuesOf(trainingData)) {
      trainingExamples.addAll(TrainSemanticParser.readCcgExamples(dataFile));
    }

    List<CcgExample> testExamples = Lists.newArrayList();
    if (options.has(testData)) {
      for (String testDataFile : options.valuesOf(testData)) {
        testExamples.addAll(TrainSemanticParser.readCcgExamples(testDataFile));
      }
    }
    Collections.shuffle(trainingExamples, Pseudorandom.get());

    // Read the various lexicons. 
    List<String> lexiconLines = IoUtils.readLines(options.valueOf(lexicon));
    List<String> unknownLexiconLines = Lists.newArrayList();
    List<String> npLexiconLines = IoUtils.readLines(options.valueOf(npLexicon));
    List<String> ruleEntries = Arrays.asList("\"DUMMY{0} DUMMY{0}\",\"(lambda $L $L)\"");
    
    // Generate feature vectors and add them as an annotation to the 
    // training examples.
    List<StringContext> contexts = StringContext.getContextsFromExamples(trainingExamples);
    FeatureVectorGenerator<StringContext> featureGen = DictionaryFeatureVectorGenerator
        .createFromData(contexts, new GeoqueryFeatureGenerator(), true);
    trainingExamples = SemanticParserUtils.annotateFeatures(trainingExamples, featureGen,
        GeoqueryUtil.FEATURE_ANNOTATION_NAME);
    testExamples = SemanticParserUtils.annotateFeatures(testExamples, featureGen,
        GeoqueryUtil.FEATURE_ANNOTATION_NAME);
    
    CcgFeatureFactory featureFactory = new GeoqueryFeatureFactory(true, true,
        GeoqueryUtil.FEATURE_ANNOTATION_NAME, featureGen.getFeatureDictionary(),
        LexiconEntry.parseLexiconEntries(npLexiconLines));

    ExpressionSimplifier simplifier = new ExpressionSimplifier(Arrays.
        <ExpressionReplacementRule>asList(new LambdaApplicationReplacementRule(),
            new VariableCanonicalizationReplacementRule(),
            new CommutativeReplacementRule("and:<t*,t>")));
    ExpressionComparator comparator = new SimplificationComparator(simplifier);

    CcgCkyInference inferenceAlgorithm = CcgCkyInference.getDefault(options.valueOf(beamSize));

    CcgParser ccgParser = GeoqueryInduceLexicon.trainSemanticParser(trainingExamples, lexiconLines,
        unknownLexiconLines, ruleEntries, featureFactory, inferenceAlgorithm, comparator,
        options.valueOf(parserIterations), options.valueOf(l2Regularization));

    String outputDirString = options.valueOf(outputDir);
    String foldNameString = options.valueOf(foldName);

    String parserModelOutputFilename = outputDirString + "/parser." + foldNameString + ".ser";
    String trainingErrorOutputFilename = outputDirString + "/training_error." + foldNameString + ".json";
    String testErrorOutputFilename = outputDirString + "/test_error." + foldNameString + ".json";
    
    IoUtils.serializeObjectToFile(ccgParser, parserModelOutputFilename);

    List<SemanticParserExampleLoss> trainingExampleLosses = Lists.newArrayList();    
    SemanticParserUtils.testSemanticParser(trainingExamples, ccgParser,
        inferenceAlgorithm, simplifier, comparator, trainingExampleLosses, true);
    SemanticParserExampleLoss.writeJsonToFile(trainingErrorOutputFilename, trainingExampleLosses);

    List<SemanticParserExampleLoss> testExampleLosses = Lists.newArrayList();    
    SemanticParserUtils.testSemanticParser(testExamples, ccgParser,
        inferenceAlgorithm, simplifier, comparator, testExampleLosses, true);
    SemanticParserExampleLoss.writeJsonToFile(testErrorOutputFilename, testExampleLosses);
  }

  public static void main(String[] args) {
    new TrainGeoqueryParser().run(args);
  }
}
