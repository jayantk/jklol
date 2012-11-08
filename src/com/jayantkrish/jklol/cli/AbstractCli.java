package com.jayantkrish.jklol.cli;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.parallel.LocalMapReduceExecutor;
import com.jayantkrish.jklol.parallel.MapReduceConfiguration;
import com.jayantkrish.jklol.training.DefaultLogFunction;
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.training.NullLogFunction;
import com.jayantkrish.jklol.training.StochasticGradientTrainer;

public abstract class AbstractCli {
  public static enum CommonOptions {
    STOCHASTIC_GRADIENT, MAP_REDUCE
  };

  private final Set<CommonOptions> opts;
  private OptionSet parsedOptions;
  
  // Help options.
  private OptionSpec<Void> helpOpt;

  // Stochastic gradient options.
  private OptionSpec<Integer> sgdIterations;
  private OptionSpec<Integer> sgdBatchSize;
  private OptionSpec<Double> sgdInitialStep;
  private OptionSpec<Double> sgdL2Regularization;
  private OptionSpec<Void> sgdBrief;

  // Map reduce options.
  private OptionSpec<Integer> mrMaxThreads;
  private OptionSpec<Integer> mrMaxBatchesPerThread;

  /**
   * Creates a command line program that accepts the specified set of
   * options.
   * 
   * @param opts
   */
  public AbstractCli(CommonOptions... opts) {
    this.opts = Sets.newHashSet(opts);
  }

  public void run(String[] args) {
    // Add and parse options.
    OptionParser parser = new OptionParser();
    initializeCommonOptions(parser);
    initializeOptions(parser);
    try {
      parsedOptions = parser.parse(args);
    } catch (OptionException e) {
      // If a help option is given, print help then quit.
      try {
        parser.printHelpOn(System.out);
      } catch (IOException ioException) {
        throw new RuntimeException(ioException);
      }
      System.exit(0);
    }

    // Log any passed-in options.
    System.out.println("Command-line options:");
    for (OptionSpec<?> optionSpec : parsedOptions.specs()) {
      if (parsedOptions.hasArgument(optionSpec)) {
        System.out.println("--" + Iterables.getFirst(optionSpec.options(), "") + " " 
            + parsedOptions.valueOf(optionSpec));
      } else {
        System.out.println("--" + Iterables.getFirst(optionSpec.options(), ""));        
      }
    }
    System.out.println("");

    // Run the program.
    processOptions(parsedOptions);
    run(parsedOptions);

    System.exit(0);
  }

  /**
   * Adds subclass-specific options to {@code parser}. Subclasses
   * should implement this method in order to accept
   * 
   * @param parser
   */
  public abstract void initializeOptions(OptionParser parser);

  /**
   * Runs the command line program using parsed {@code options}.
   * 
   * @param options
   */
  public abstract void run(OptionSet options);

  /**
   * Adds common options to {@code parser}.
   * 
   * @param parser
   */
  private void initializeCommonOptions(OptionParser parser) {
    helpOpt = parser.acceptsAll(Arrays.asList("help", "h"), "Print this help message.");
    
    if (opts.contains(CommonOptions.STOCHASTIC_GRADIENT)) {
      sgdIterations = parser.accepts("iterations", 
          "Number of iterations (passes over the data) for stochastic gradient descent.").
          withRequiredArg().ofType(Integer.class).defaultsTo(10);
      sgdBatchSize = parser.accepts("batchSize", 
          "Minibatch size, i.e., the number of examples processed per gradient computation.")
          .withRequiredArg().ofType(Integer.class).defaultsTo(1);
      sgdInitialStep = parser.accepts("initialStepSize", 
          "Initial step size for stochastic gradient descent.")
          .withRequiredArg().ofType(Double.class).defaultsTo(1.0);
      sgdL2Regularization = parser.accepts("l2Regularization", 
          "Regularization parameter for the L2 norm of the parameter vector.")
          .withRequiredArg().ofType(Double.class).defaultsTo(0.1);
      // boolean option.
      sgdBrief = parser.accepts("brief", "Hides training output."); 
    }
    
    if (opts.contains(CommonOptions.MAP_REDUCE)) {
      mrMaxThreads = parser.accepts("maxThreads", 
          "Maximum number of threads to use during parallel execution.")
          .withRequiredArg().ofType(Integer.class).defaultsTo(Runtime.getRuntime().availableProcessors());
      mrMaxBatchesPerThread = parser.accepts("maxBatchesPerThread", 
          "Number of batches of items to create per thread.")
          .withRequiredArg().ofType(Integer.class).defaultsTo(20);
    }
  }

  /**
   * Initializes program state using any options processable by this
   * class.
   * 
   * @param options
   */
  private void processOptions(OptionSet options) {
    if (opts.contains(CommonOptions.MAP_REDUCE)) {
      MapReduceConfiguration.setMapReduceExecutor(new LocalMapReduceExecutor(
          options.valueOf(mrMaxThreads), options.valueOf(mrMaxBatchesPerThread)));
    }
  }

  /**
   * Creates a {@code StochasticGradientTrainer} configured using the
   * provided options. In order to use this method, pass
   * {@link CommonOptions#STOCHASTIC_GRADIENT} to the constructor.
   * 
   * @param options
   * @param numExamples
   * @return
   */
  protected StochasticGradientTrainer createStochasticGradientTrainer(int numExamples) {
    Preconditions.checkState(opts.contains(CommonOptions.STOCHASTIC_GRADIENT));

    int iterationsOption = parsedOptions.valueOf(sgdIterations);
    int batchSize = parsedOptions.valueOf(sgdBatchSize);
    int numIterations = (int) Math.ceil(iterationsOption * numExamples / ((double) batchSize));
    double initialStepSize = parsedOptions.valueOf(sgdInitialStep);
    double l2Regularization = parsedOptions.valueOf(sgdL2Regularization);
    boolean brief = parsedOptions.has(sgdBrief);

    LogFunction log = (brief ? new NullLogFunction() : new DefaultLogFunction());
    StochasticGradientTrainer trainer = StochasticGradientTrainer.createWithL2Regularization(
        numIterations, batchSize, initialStepSize, true, l2Regularization, log);

    return trainer;
  }
}
