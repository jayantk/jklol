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
import com.jayantkrish.jklol.training.DefaultLogFunction;
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
    OptionSet options = parser.parse(args);
    
    // Read in the lexicon to instantiate the model. 
    List<String> lexiconEntries = IoUtils.readLines(options.valueOf(lexicon));
    ParametricCcgParser family = ParametricCcgParser.parseFromLexicon(lexiconEntries);
    
    // Read in training data.
    List<CcgExample> trainingExamples = Lists.newArrayList();
    for (String line : IoUtils.readLines(options.valueOf(trainingData))) {
      trainingExamples.add(CcgExample.parseFromString(line));
    }
    
    // Train the model.
    CcgLoglikelihoodOracle oracle = new CcgLoglikelihoodOracle(family, 10);
    StochasticGradientTrainer trainer = StochasticGradientTrainer.createWithL2Regularization(10, 1, 1, 
        true, 0.1, new DefaultLogFunction());
    SufficientStatistics parameters = trainer.train(oracle, oracle.initializeGradient(), trainingExamples);
    CcgParser ccgParser = family.getParserFromParameters(parameters);

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
  }
}
