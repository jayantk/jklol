package com.jayantkrish.jklol.cli;

import java.util.Collections;
import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.CcgLoglikelihoodOracle;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.StochasticGradientTrainer;
import com.jayantkrish.jklol.util.IoUtils;

/**
 * Estimates parameters for a CCG parser given a lexicon and a 
 * set of training data. The training data consists of sentences
 * with annotations of the correct dependency structures.  
 * 
 * @author jayantk
 */
public class TrainCcg extends AbstractCli {
  
  private static final String DISCARD_INVALID_OPT = "discardInvalid";

  private OptionSpec<String> lexicon;
  private OptionSpec<String> trainingData;
  private OptionSpec<String> modelOutput;
  private OptionSpec<String> rules;
  private OptionSpec<Integer> beamSize;

  public TrainCcg() {
    super(CommonOptions.STOCHASTIC_GRADIENT, CommonOptions.MAP_REDUCE);
  }
  
  @Override
  public void initializeOptions(OptionParser parser) {
    // Required arguments.
    lexicon = parser.accepts("lexicon").withRequiredArg().ofType(String.class).required();
    trainingData = parser.accepts("trainingData").withRequiredArg().ofType(String.class).required();
    modelOutput = parser.accepts("output").withRequiredArg().ofType(String.class).required();
    // Optional options
    rules = parser.accepts("rules").withRequiredArg().ofType(String.class);
    beamSize = parser.accepts("beamSize").withRequiredArg().ofType(Integer.class).defaultsTo(100);
    parser.accepts(DISCARD_INVALID_OPT);
  }

  @Override
  public void run(OptionSet options) {
    // Read in the lexicon to instantiate the model.
    List<String> lexiconEntries = IoUtils.readLines(options.valueOf(lexicon));
    List<String> ruleEntries = options.has(rules) ? IoUtils.readLines(options.valueOf(rules))
        : Collections.<String>emptyList();
    ParametricCcgParser family = ParametricCcgParser.parseFromLexicon(lexiconEntries, ruleEntries);
    
    // Read in training data.
    List<CcgExample> trainingExamples = Lists.newArrayList();
    int numDiscarded = 0;
    for (String line : IoUtils.readLines(options.valueOf(trainingData))) {
      CcgExample example = CcgExample.parseFromString(line);
      if (family.isValidExample(example)) {
        trainingExamples.add(example);
      } else {
        Preconditions.checkState(options.has(DISCARD_INVALID_OPT), "Invalid example: %s", example);
        System.out.println("Discarding example: " + example);
        numDiscarded++;
      }
    }
    System.out.println(lexiconEntries.size() + " lexicon entries.");
    System.out.println(trainingExamples.size() + " training examples.");
    System.out.println(numDiscarded + " discarded training examples.");
    
    // Train the model.
    CcgLoglikelihoodOracle oracle = new CcgLoglikelihoodOracle(family, options.valueOf(beamSize));
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
