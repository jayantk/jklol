package com.jayantkrish.jklol.cli;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

import com.jayantkrish.jklol.training.DefaultLogFunction;
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.training.NullLogFunction;
import com.jayantkrish.jklol.training.StochasticGradientTrainer;

/**
 * Static methods for handling common types of options.
 * 
 * @author jayant
 */
public class OptionUtils {

  /**
   * Adds several optional options to the given parser, which enable configuration
   * of a {@code StochasticGradientTrainer}. The trainer is retrievable using
   * {@link #createStochasticGradientTrainer}.
   * 
   * @param parser
   */
  public static void addStochasticGradientOptions(OptionParser parser) {
    parser.accepts("iterations").withRequiredArg().ofType(Integer.class).defaultsTo(10);
    parser.accepts("batchSize").withRequiredArg().ofType(Integer.class).defaultsTo(1);
    parser.accepts("initialStepSize").withRequiredArg().ofType(Double.class).defaultsTo(1.0);
    parser.accepts("l2Regularization").withRequiredArg().ofType(Double.class).defaultsTo(0.1);
    // boolean options.
    parser.accepts("brief"); // Hides training output.
  }
  
  /**
   * Creates a {@code StochasticGradientTrainer} configured using the provided
   * options.
   * 
   * @param options
   * @param numExamples
   * @return
   */
  public static StochasticGradientTrainer createStochasticGradientTrainer(OptionSet options, 
      int numExamples) {
    int iterationsOption = (Integer) options.valueOf("iterations");
    int batchSize = (Integer) options.valueOf("batchSize");
    int numIterations = (int) Math.ceil(iterationsOption * numExamples / ((double) batchSize));
    double initialStepSize = (Double) options.valueOf("initialStepSize");
    double l2Regularization = (Double) options.valueOf("l2Regularization");
    boolean brief = options.has("brief");

    LogFunction log = (brief ? new NullLogFunction() : new DefaultLogFunction());
    StochasticGradientTrainer trainer = StochasticGradientTrainer.createWithL2Regularization(
        numIterations, batchSize, initialStepSize, true, l2Regularization, log);

    return trainer;
  }
}
