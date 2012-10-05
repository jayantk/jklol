package com.jayantkrish.jklol.cli;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.CcgLoglikelihoodOracle;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.models.parametric.TensorBuilderSufficientStatistics;
import com.jayantkrish.jklol.training.DefaultLogFunction;
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.training.NullLogFunction;
import com.jayantkrish.jklol.training.StochasticGradientTrainer;
import com.jayantkrish.jklol.util.IoUtils;

/**
 * Estimates parameters for a CCG parser given a lexicon and a 
 * set of training data. The training data consists of sentences
 * with annotations of the correct dependency structures.  
 * 
 * @author jayantk
 */
public class TrainCcg {

  public static void main(String[] args) {
    OptionParser parser = new OptionParser();
    // Required arguments.
    OptionSpec<String> lexicon = parser.accepts("lexicon").withRequiredArg().ofType(String.class).required();
    OptionSpec<String> trainingData = parser.accepts("trainingData").withRequiredArg().ofType(String.class).required();
    OptionSpec<String> modelOutput = parser.accepts("output").withRequiredArg().ofType(String.class).required();
    // Optional options
    OptionSpec<Integer> iterations = parser.accepts("iterations").withRequiredArg().ofType(Integer.class).defaultsTo(10);
    OptionSpec<Integer> beamSize = parser.accepts("beamSize").withRequiredArg().ofType(Integer.class).defaultsTo(100);
    OptionSpec<Double> initialStepSize = parser.accepts("initialStepSize").withRequiredArg().ofType(Double.class).defaultsTo(1.0);
    OptionSpec<Double> l2Regularization = parser.accepts("l2Regularization").withRequiredArg().ofType(Double.class).defaultsTo(0.1);
    // boolean options.
    parser.accepts("brief"); // Hides training output.
    OptionSet options = parser.parse(args);
    
    // Read in the lexicon to instantiate the model. 
    List<String> lexiconEntries = IoUtils.readLines(options.valueOf(lexicon));
    ParametricCcgParser family = ParametricCcgParser.parseFromLexicon(lexiconEntries);
    
    // Read in training data.
    List<CcgExample> trainingExamples = Lists.newArrayList();
    for (String line : IoUtils.readLines(options.valueOf(trainingData))) {
      trainingExamples.add(CcgExample.parseFromString(line));
    }
    System.out.println(lexiconEntries.size() + " lexicon entries.");
    System.out.println(trainingExamples.size() + " training examples.");
    
    // Train the model.
    CcgLoglikelihoodOracle oracle = new CcgLoglikelihoodOracle(family, options.valueOf(beamSize));
    int numIterations = options.valueOf(iterations) * trainingExamples.size();
    LogFunction log = (options.has("brief")) ? new NullLogFunction() : new DefaultLogFunction();
    StochasticGradientTrainer trainer = StochasticGradientTrainer.createWithL2Regularization(
        numIterations, 1, options.valueOf(initialStepSize), true, options.valueOf(l2Regularization),
        log);
    SufficientStatistics parameters = trainer.train(oracle, oracle.initializeGradient(), trainingExamples);
    CcgParser ccgParser = family.getParserFromParameters(parameters);

    System.out.println("Serializing trained model...");
    // Serialize and write out model. 
    FileOutputStream fos = null;
    ObjectOutputStream out = null;
    try {
      fos = new FileOutputStream(options.valueOf(modelOutput));
      out = new ObjectOutputStream(fos);
      out.writeObject(ccgParser);
      out.close();
    } catch(IOException ex) {
      ex.printStackTrace();
      System.exit(1);
    }
    
    List<SufficientStatistics> list = parameters.coerceToList().getStatistics();
    TensorBuilderSufficientStatistics t0 = (TensorBuilderSufficientStatistics) list.get(0);
    TensorBuilderSufficientStatistics t1 = (TensorBuilderSufficientStatistics) list.get(1);
    System.out.println(t0.get());
    System.out.println(t1.get());
    
    System.exit(0);
  }
}
