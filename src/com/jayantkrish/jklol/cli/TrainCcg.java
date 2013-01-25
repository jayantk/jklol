package com.jayantkrish.jklol.cli;

import java.util.List;
import java.util.Set;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.CcgLoglikelihoodOracle;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.CcgPerceptronOracle;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.GradientOracle;
import com.jayantkrish.jklol.training.StochasticGradientTrainer;
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
  private OptionSpec<Integer> beamSize;
  private OptionSpec<Void> useCcgBankFormat;
  private OptionSpec<Void> perceptron;
  private OptionSpec<Void> discardInvalid;
  private OptionSpec<Void> ignoreSemantics;

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
    beamSize = parser.accepts("beamSize").withRequiredArg().ofType(Integer.class).defaultsTo(100);
    useCcgBankFormat = parser.accepts("useCcgBankFormat");
    perceptron = parser.accepts("perceptron");
    discardInvalid = parser.accepts("discardInvalid");
    ignoreSemantics = parser.accepts("ignoreSemantics");
  }

  @Override
  public void run(OptionSet options) {
    // Read in all of the provided training examples.
    List<CcgExample> unfilteredTrainingExamples = CcgExample.readExamplesFromFile(
        options.valueOf(trainingData), options.has(useCcgBankFormat), options.has(ignoreSemantics));
    Set<String> posTags = CcgExample.getPosTagVocabulary(unfilteredTrainingExamples);
    System.out.println(posTags.size() + " POS tags");

    // Create the CCG parser from the provided options.
    System.out.println("Creating ParametricCcgParser.");
    ParametricCcgParser family = createCcgParser(posTags);
    System.out.println("Done creating ParametricCcgParser.");

    // Read in training data and confirm its validity.
    CcgParser parser = family.getModelFromParameters(family.getNewSufficientStatistics());
    List<CcgExample> trainingExamples = parser.filterExampleCollection(unfilteredTrainingExamples,
        !options.has(discardInvalid));
    System.out.println(trainingExamples.size() + " training examples.");
    int numDiscarded = unfilteredTrainingExamples.size() - trainingExamples.size();
    System.out.println(numDiscarded + " discarded training examples.");
    
    // Train the model.
    GradientOracle<CcgParser, CcgExample> oracle = null;
    if (options.has(perceptron)) {
      oracle = new CcgPerceptronOracle(family, options.valueOf(beamSize));
    } else {
      oracle = new CcgLoglikelihoodOracle(family, options.valueOf(beamSize));
    }
    StochasticGradientTrainer trainer = createStochasticGradientTrainer(trainingExamples.size());
    SufficientStatistics parameters = trainer.train(oracle, oracle.initializeGradient(),
        trainingExamples);
    CcgParser ccgParser = family.getModelFromParameters(parameters);

    System.out.println("Serializing trained model...");
    IoUtils.serializeObjectToFile(ccgParser, options.valueOf(modelOutput));

    System.out.println("Trained model parameters:");
    System.out.println(family.getParameterDescription(parameters));
  }

  public static void main(String[] args) {
    new TrainCcg().run(args);
  }
}
