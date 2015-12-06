package com.jayantkrish.jklol.ccg.cli;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgBeamSearchInference;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.CcgFeatureFactory;
import com.jayantkrish.jklol.ccg.CcgInference;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.CcgPerceptronOracle;
import com.jayantkrish.jklol.ccg.DefaultCcgFeatureFactory;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda2.CommutativeReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionComparator;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.lambda2.LambdaApplicationReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.SimplificationComparator;
import com.jayantkrish.jklol.ccg.lambda2.VariableCanonicalizationReplacementRule;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.training.GradientOptimizer;
import com.jayantkrish.jklol.training.GradientOracle;
import com.jayantkrish.jklol.util.IoUtils;

public class TrainSemanticParser extends AbstractCli {
  
  // CCG parser options
  private OptionSpec<String> ccgLexicon;
  private OptionSpec<String> ccgUnknownLexicon;
  private OptionSpec<String> ccgRules;
  private OptionSpec<Void> ccgApplicationOnly;
  private OptionSpec<Void> ccgNormalFormOnly;
  private OptionSpec<Void> skipWords;

  // Text with annotated logical forms to train the parser.
  private OptionSpec<String> jsonTrainingData;
  private OptionSpec<String> trainingData;

  private OptionSpec<Integer> beamSize;

  // Where the trained parser is saved.
  private OptionSpec<String> modelOutput;
  
  public static final String START_WORD = "**start**";

  public TrainSemanticParser() {
    super(CommonOptions.STOCHASTIC_GRADIENT, CommonOptions.MAP_REDUCE);
  }

  @Override
  public void initializeOptions(OptionParser parser) {
    // Required arguments.
    ccgLexicon = parser.accepts("lexicon", "The CCG lexicon defining the grammar to use.")
        .withRequiredArg().ofType(String.class).required();
    ccgUnknownLexicon = parser.accepts("unknownLexicon", "The CCG lexicon for unknown words.")
        .withRequiredArg().ofType(String.class);

    // Optional arguments
    ccgRules = parser.accepts("rules",
        "Binary and unary rules to use during CCG parsing, in addition to function application and composition.")
        .withRequiredArg().ofType(String.class).required();
    modelOutput = parser.accepts("output").withRequiredArg().ofType(String.class).required();
    
    // At least one of the training data options is required. 
    jsonTrainingData = parser.accepts("jsonTrainingData").withRequiredArg().ofType(String.class);
    trainingData = parser.accepts("trainingData").withRequiredArg().ofType(String.class);

    ccgApplicationOnly = parser.accepts("applicationOnly",
        "Use only function application during parsing, i.e., no composition.");
    ccgNormalFormOnly = parser.accepts("normalFormOnly",
        "Only permit CCG derivations in Eisner normal form.");
    skipWords = parser.accepts("skipWords", "Allow the parser to skip words in the parse");

    beamSize = parser.accepts("beamSize").withRequiredArg().ofType(Integer.class).defaultsTo(100);
  }

  @Override
  public void run(OptionSet options) {
    List<CcgExample> trainingExamples = null; 
    if (options.has(jsonTrainingData)) {
      trainingExamples = readCcgExamplesJson(options.valueOf(jsonTrainingData));
    } else if (options.has(trainingData)) {
      trainingExamples = readCcgExamples(options.valueOf(trainingData));
    }

    Preconditions.checkState(trainingExamples != null);
    System.out.println("Read " + trainingExamples.size() + " training examples");

    ParametricCcgParser family = createCcgParser(options);

    ExpressionSimplifier simplifier = new ExpressionSimplifier(Arrays.
        <ExpressionReplacementRule>asList(new LambdaApplicationReplacementRule(),
            new VariableCanonicalizationReplacementRule(),
            new CommutativeReplacementRule("and:<t*,t>")));
    ExpressionComparator comparator = new SimplificationComparator(simplifier);

    CcgInference inferenceAlgorithm = new CcgBeamSearchInference(null, comparator, options.valueOf(beamSize),
        -1, Integer.MAX_VALUE, Runtime.getRuntime().availableProcessors(), false);
    GradientOracle<CcgParser, CcgExample> oracle = new CcgPerceptronOracle(family,
        inferenceAlgorithm, 0.0);
    /*
    GradientOracle<CcgParser, CcgExample> oracle = new CcgLoglikelihoodOracle(family,
        comparator, options.valueOf(beamSize));
        */ 

    GradientOptimizer trainer = createGradientOptimizer(trainingExamples.size());
    SufficientStatistics parameters = trainer.train(oracle, oracle.initializeGradient(),
        trainingExamples);
    CcgParser ccgParser = family.getModelFromParameters(parameters);

    System.out.println("Serializing trained model...");
    IoUtils.serializeObjectToFile(ccgParser, options.valueOf(modelOutput));

    System.out.println("Trained model parameters:");
    System.out.println(family.getParameterDescription(parameters));
  }

  public static List<CcgExample> readCcgExamplesJson(String jsonFilename) {
    List<CcgExample> examples = Lists.newArrayList();
    try {
      ExpressionParser<Expression2> lfParser = ExpressionParser.expression2();
      ObjectMapper mapper = new ObjectMapper();
      JsonNode rootNode = mapper.readTree(new File(jsonFilename));
      Iterator<JsonNode> iter = rootNode.elements();
      while (iter.hasNext()) {
        JsonNode exampleNode = iter.next();
        
        String utterance = exampleNode.get("utterance").asText();
        String targetFormula = exampleNode.get("targetFormula").asText();

        List<String> words = Arrays.asList(utterance.split("\\s"));
        Expression2 lf = lfParser.parse(targetFormula);

        // Parts-of-speech are assumed to be unknown.
        List<String> posTags = Collections.nCopies(words.size(), ParametricCcgParser.DEFAULT_POS_TAG);
        AnnotatedSentence sentence = new AnnotatedSentence(words, posTags);

        CcgExample example = new CcgExample(sentence, null, null, lf);
        examples.add(example);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return examples;
  }

  public static List<CcgExample> readCcgExamples(String filename) {
    List<String> lines = IoUtils.readLines(filename);
    List<CcgExample> examples = Lists.newArrayList();
    List<String> words = null;
    Expression2 expression = null;
    ExpressionParser<Expression2> parser = ExpressionParser.expression2();
    for (String line : lines) {
      if (line.trim().length() == 0 && words != null && expression != null) {
        words = null;
        expression = null;
      } else if (line.startsWith("(")) {
        expression = parser.parse(line);

        List<String> posTags = Collections.nCopies(words.size(), ParametricCcgParser.DEFAULT_POS_TAG);
        AnnotatedSentence supertaggedSentence = new AnnotatedSentence(words, posTags);

        examples.add(new CcgExample(supertaggedSentence, null, null, expression));
      } else {
        words = Arrays.asList(line.split("\\s"));
      }
    }

    return examples;
  }

  private ParametricCcgParser createCcgParser(OptionSet parsedOptions) {
    CcgFeatureFactory featureFactory = new DefaultCcgFeatureFactory(false, parsedOptions.has(skipWords));
    // Read in the lexicon to instantiate the model.
    List<String> lexiconEntries = IoUtils.readLines(parsedOptions.valueOf(ccgLexicon));
    List<String> unknownLexiconEntries = parsedOptions.has(ccgUnknownLexicon) ?
        IoUtils.readLines(parsedOptions.valueOf(ccgUnknownLexicon)) : Collections.<String> emptyList();
    List<String> ruleEntries = parsedOptions.has(ccgRules) ? IoUtils.readLines(parsedOptions.valueOf(ccgRules))
        : Collections.<String> emptyList();

    return ParametricCcgParser.parseFromLexicon(lexiconEntries, unknownLexiconEntries, ruleEntries,
        featureFactory, null, !parsedOptions.has(ccgApplicationOnly), null,
        parsedOptions.has(ccgNormalFormOnly));
  }

  public static void main(String[] args) {
    new TrainSemanticParser().run(args);
  }
}
