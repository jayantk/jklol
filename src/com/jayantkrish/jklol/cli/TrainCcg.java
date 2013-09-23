package com.jayantkrish.jklol.cli;

import java.util.List;
import java.util.Map;
import java.util.Set;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.CcgLoglikelihoodOracle;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.CcgPerceptronOracle;
import com.jayantkrish.jklol.ccg.CcgRuleSchema;
import com.jayantkrish.jklol.ccg.CcgSyntaxTree;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.ccg.SyntacticCategory;
import com.jayantkrish.jklol.ccg.chart.SyntacticChartFilter.DefaultCompatibilityFunction;
import com.jayantkrish.jklol.ccg.chart.SyntacticChartFilter.SyntacticCompatibilityFunction;
import com.jayantkrish.jklol.ccg.data.CcgExampleReader;
import com.jayantkrish.jklol.ccg.data.CcgSyntaxTreeReader;
import com.jayantkrish.jklol.ccg.data.CcgbankSyntaxTreeReader;
import com.jayantkrish.jklol.data.DataFormat;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
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
  private OptionSpec<Void> useCcgBankFormat;
  private OptionSpec<Void> perceptron;
  private OptionSpec<Void> discardInvalid;
  private OptionSpec<Void> ignoreSemantics;
  private OptionSpec<Void> onlyObservedBinaryRules;

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
    useCcgBankFormat = parser.accepts("useCcgBankFormat");
    perceptron = parser.accepts("perceptron");
    discardInvalid = parser.accepts("discardInvalid");
    ignoreSemantics = parser.accepts("ignoreSemantics");
    onlyObservedBinaryRules = parser.accepts("onlyObservedBinaryRules");
  }

  @Override
  public void run(OptionSet options) {
    List<CcgExample> unfilteredTrainingExamples = readTrainingData(options.valueOf(trainingData),
        options.has(ignoreSemantics), options.has(useCcgBankFormat), options.valueOf(syntaxMap));
    Set<String> posTags = CcgExample.getPosTagVocabulary(unfilteredTrainingExamples);
    System.out.println(posTags.size() + " POS tags");

    Set<CcgRuleSchema> observedRules = null;
    if (options.has(onlyObservedBinaryRules)) {
      observedRules = Sets.newHashSet();
      for (CcgExample example : unfilteredTrainingExamples) {
        observedRules.addAll(example.getSyntacticParse().getObservedBinaryRules());
      }
    }

    // Create the CCG parser from the provided options.
    System.out.println("Creating ParametricCcgParser.");
    ParametricCcgParser family = createCcgParser(posTags, observedRules, null);
    System.out.println("Done creating ParametricCcgParser.");

    // Read in training data and confirm its validity.
    CcgParser parser = family.getModelFromParameters(family.getNewSufficientStatistics());

    System.out.println(parser.getSyntaxDistribution().getParameterDescription());

    Multimap<SyntacticCategory, HeadedSyntacticCategory> syntaxMultimap = parser.getSyntacticCategoryMap();
    List<CcgExample> trainingExamples = parser.filterExampleCollection(unfilteredTrainingExamples,
        !options.has(discardInvalid), syntaxMultimap);
    System.out.println(trainingExamples.size() + " training examples.");
    int numDiscarded = unfilteredTrainingExamples.size() - trainingExamples.size();
    System.out.println(numDiscarded + " discarded training examples.");
    
    // Train the model.
    GradientOracle<CcgParser, CcgExample> oracle = null;
    SyntacticCompatibilityFunction compatibilityFunction = new DefaultCompatibilityFunction();
    if (options.has(perceptron)) {
      oracle = new CcgPerceptronOracle(family, null, compatibilityFunction, options.valueOf(beamSize));
    } else {
      oracle = new CcgLoglikelihoodOracle(family, compatibilityFunction, options.valueOf(beamSize));
    }
    GradientOptimizer trainer = createGradientOptimizer(trainingExamples.size());
    SufficientStatistics parameters = trainer.train(oracle, oracle.initializeGradient(),
        trainingExamples);
    CcgParser ccgParser = family.getModelFromParameters(parameters);

    System.out.println("Serializing trained model...");
    IoUtils.serializeObjectToFile(ccgParser, options.valueOf(modelOutput));

    System.out.println("Trained model parameters:");
    System.out.println(family.getParameterDescription(parameters));
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

      syntaxTreeReader = new CcgbankSyntaxTreeReader(syntacticCategoryMap);
    } else {
      syntaxTreeReader = new CcgSyntaxTreeReader();
    }
    CcgExampleReader exampleReader = new CcgExampleReader(syntaxTreeReader, ignoreSemantics);
    return exampleReader.parseFromFile(filename);
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
}
