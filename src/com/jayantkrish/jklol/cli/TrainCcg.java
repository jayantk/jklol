package com.jayantkrish.jklol.cli;

import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
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
  private OptionSpec<Void> perceptron;
  private OptionSpec<Void> discardInvalid;

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
    perceptron = parser.accepts("perceptron");
    discardInvalid = parser.accepts("discardInvalid");
  }

  @Override
  public void run(OptionSet options) {
    // Create the CCG parser from the provided options.
    ParametricCcgParser family = createCcgParser();

    // Read in training data.
    List<CcgExample> trainingExamples = Lists.newArrayList();
    int numDiscarded = 0;
    for (String line : IoUtils.readLines(options.valueOf(trainingData))) {
      CcgExample example = CcgExample.parseFromString(line);
      if (family.isValidExample(example)) {
        trainingExamples.add(example);
      } else {
        Preconditions.checkState(options.has(discardInvalid), "Invalid example: %s", example);
        System.out.println("Discarding example: " + example);
        numDiscarded++;
      }
    }
    System.out.println(trainingExamples.size() + " training examples.");
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
