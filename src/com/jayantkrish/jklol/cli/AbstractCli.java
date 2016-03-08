package com.jayantkrish.jklol.cli;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.boost.FunctionalGradientAscent;
import com.jayantkrish.jklol.dtree.RegressionTreeTrainer;
import com.jayantkrish.jklol.parallel.LocalMapReduceExecutor;
import com.jayantkrish.jklol.parallel.MapReduceConfiguration;
import com.jayantkrish.jklol.sequence.cli.TrainSequenceModel;
import com.jayantkrish.jklol.training.DefaultLogFunction;
import com.jayantkrish.jklol.training.GradientOptimizer;
import com.jayantkrish.jklol.training.Lbfgs;
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.training.LogFunctions;
import com.jayantkrish.jklol.training.MinibatchLbfgs;
import com.jayantkrish.jklol.training.NullLogFunction;
import com.jayantkrish.jklol.training.StochasticGradientTrainer;
import com.jayantkrish.jklol.util.Pseudorandom;
import com.jayantkrish.jklol.util.TimeUtils;

/**
 * Common framework for command line programs. This class provides
 * option parsing functionality and implementations of commonly-used
 * option sets, such as an option to seed the random number generator.
 * This class also provides basic logging functionality for the
 * passed-in options, making experiments easier to repeat.
 * <p>
 * Command-line programs using this class should implement all
 * abstract methods, then write a main method which instantiates a
 * class instance and invokes {@link #run}. See
 * {@link TrainSequenceModel} for an example.
 * 
 * @author jayantk
 */
public abstract class AbstractCli {

  /**
   * Common sets of options which subclasses may optionally choose to
   * accept. To accept a given set of options, include the
   * corresponding value in the constructor for {@code AbstractCli}.
   */
  public static enum CommonOptions {
    /**
     * Enables options for constructing a
     * {@code StochasticGradientTrainer}. For example, these options
     * include the number of training iterations and regularization
     * parameters.
     */
    STOCHASTIC_GRADIENT,
    /**
     * Enables options for constructing a {@code Lbfgs} trainer.
     */
    LBFGS,
    /**
     * Enables parallelization options configuring the execution of
     * embarassingly parallel tasks. These options set the default
     * {@code MapReduceExecutor} used by the program. For example,
     * these options include the maximum number of threads to use.
     */
    MAP_REDUCE,
    /**
     * Enables options for performing boosting via functional gradient
     * ascent.
     */
    FUNCTIONAL_GRADIENT_ASCENT,
    /**
     * Enables options for training regression trees.
     */
    REGRESSION_TREE,
  };

  private final Set<CommonOptions> opts;
  private OptionSet parsedOptions;

  // Help options.
  protected OptionSpec<Void> helpOpt;

  // Always available options.
  // Seed the random number generator
  protected OptionSpec<Long> randomSeed;
  // Prevents the program from printing out the input options
  protected OptionSpec<Void> noPrintOptions;

  // Stochastic gradient options.
  protected OptionSpec<Long> sgdIterations;
  protected OptionSpec<Integer> sgdBatchSize;
  protected OptionSpec<Double> sgdInitialStep;
  protected OptionSpec<Void> sgdNoDecayStepSize;
  protected OptionSpec<Void> sgdNoReturnAveragedParameters;
  protected OptionSpec<Double> sgdL2Regularization;
  protected OptionSpec<Double> sgdRegularizationFrequency;
  protected OptionSpec<Void> sgdAdagrad;

  // LBFGS options.
  protected OptionSpec<Void> lbfgs;
  protected OptionSpec<Integer> lbfgsIterations;
  protected OptionSpec<Integer> lbfgsHessianRank;
  protected OptionSpec<Double> lbfgsL2Regularization;
  protected OptionSpec<Integer> lbfgsMinibatchSize;
  protected OptionSpec<Integer> lbfgsMinibatchIterations;
  protected OptionSpec<Void> lbfgsAdaptiveMinibatches;

  // Logging options for all optimization algorithms.
  protected OptionSpec<Integer> logInterval;
  protected OptionSpec<Integer> logParametersInterval;
  protected OptionSpec<String> logParametersDir;
  protected OptionSpec<Void> logBrief;

  // Map reduce options.
  protected OptionSpec<Integer> mrMaxThreads;
  protected OptionSpec<Integer> mrMaxBatchesPerThread;

  // Functional gradient ascent options
  protected OptionSpec<Integer> fgaIterations;
  protected OptionSpec<Integer> fgaBatchSize;
  protected OptionSpec<Double> fgaInitialStep;
  protected OptionSpec<Void> fgaNoDecayStepSize;

  // Regression tree options
  protected OptionSpec<Integer> rtreeMaxDepth;

  /**
   * Creates a command line program that accepts the specified set of
   * options.
   * 
   * @param opts any optional option sets to accept
   */
  public AbstractCli(CommonOptions... opts) {
    this.opts = Sets.newHashSet(opts);
  }

  /**
   * Runs the program, parsing any options from {@code args}.
   * 
   * @param args arguments to the program, in the same format as
   * provided by {@code main}.
   */
  public void run(String[] args) {
    // Add and parse options.
    OptionParser parser = new OptionParser();
    initializeCommonOptions(parser);
    initializeOptions(parser);

    String errorMessage = null;
    try {
      parsedOptions = parser.parse(args);
    } catch (OptionException e) {
      errorMessage = e.getMessage();
    }

    boolean printHelp = false;
    if (errorMessage != null) {
      // If an error occurs, the options don't parse.
      // Therefore, we must manually check if the help option was
      // given.
      for (int i = 0; i < args.length; i++) {
        if (args[i].equals("--help")) {
          printHelp = true;
        }
      }
    }

    if (errorMessage != null && !printHelp) {
      System.err.println(errorMessage);
      System.err.println("Try --help for more information about options.");
      System.exit(1);
    }

    if (printHelp || parsedOptions.has(helpOpt)) {
      // If a help option is given, print help then quit.
      try {
        parser.printHelpOn(System.err);
      } catch (IOException ioException) {
        throw new RuntimeException(ioException);
      }
      System.exit(0);
    }

    // Log any passed-in options.
    if (!parsedOptions.has(noPrintOptions)) {
      System.out.println("Command-line options:");
      for (OptionSpec<?> optionSpec : parsedOptions.specs()) {
        if (parsedOptions.hasArgument(optionSpec)) {
          System.out.println("--" + Iterables.getFirst(optionSpec.options(), "") + " "
              + Joiner.on(" ").join(parsedOptions.valuesOf(optionSpec)));
        } else {
          System.out.println("--" + Iterables.getFirst(optionSpec.options(), ""));
        }
      }
      System.out.println("");
    }

    // Run the program.
    long startTime = System.currentTimeMillis();
    processOptions(parsedOptions);
    run(parsedOptions);
    long endTime = System.currentTimeMillis();

    if (!parsedOptions.has(noPrintOptions)) {
      System.out.println("Total time elapsed: " + TimeUtils.durationToString(endTime - startTime));
    }

    System.exit(0);
  }

  /**
   * Adds subclass-specific options to {@code parser}. Subclasses must
   * implement this method in order to accept class-specific options.
   * 
   * @param parser option parser to which additional command-line
   * options should be added.
   */
  public abstract void initializeOptions(OptionParser parser);

  /**
   * Runs the program using parsed {@code options}.
   * 
   * @param options option values passed to the program
   */
  public abstract void run(OptionSet options);

  /**
   * Adds common options to {@code parser}.
   * 
   * @param parser
   */
  private void initializeCommonOptions(OptionParser parser) {
    helpOpt = parser.acceptsAll(Arrays.asList("help", "h"), "Print this help message.");

    randomSeed = parser.accepts("randomSeed", "Seed to use for generating random numbers. "
        + "Program execution may still be nondeterministic, if multithreading is used.").
        withRequiredArg().ofType(Long.class).defaultsTo(0L);
    
    noPrintOptions = parser.accepts("noPrintOptions", "Don't print out the command-line options "
        + "passed in to this program or final runtime statistics.");

    if (opts.contains(CommonOptions.STOCHASTIC_GRADIENT)) {
      sgdIterations = parser.accepts("iterations",
          "Number of iterations (passes over the data) for stochastic gradient descent.").
          withRequiredArg().ofType(Long.class).defaultsTo(10L);
      sgdBatchSize = parser.accepts("batchSize",
          "Minibatch size, i.e., the number of examples processed per gradient computation. If unspecified, defaults to using the entire data set (gradient descent).")
          .withRequiredArg().ofType(Integer.class);
      sgdInitialStep = parser.accepts("initialStepSize",
          "Initial step size for stochastic gradient descent.")
          .withRequiredArg().ofType(Double.class).defaultsTo(1.0);
      sgdNoDecayStepSize = parser.accepts("noDecayStepSize",
          "Don't use a 1/sqrt(t) step size decay during stochastic gradient descent.");
      sgdNoReturnAveragedParameters = parser.accepts("noReturnAveragedParameters", 
          "Get the average of the parameter iterates of stochastic gradient descent.");
      sgdL2Regularization = parser.accepts("l2Regularization",
          "Regularization parameter for the L2 norm of the parameter vector.")
          .withRequiredArg().ofType(Double.class).defaultsTo(0.0);
      sgdRegularizationFrequency = parser.accepts("regularizationFrequency",
          "Fraction of iterations on which to apply regularization. Must be between 0 and 1")
          .withRequiredArg().ofType(Double.class).defaultsTo(1.0);
      sgdAdagrad = parser.accepts("adagrad", "Use the adagrad algorithm for stochastic gradient descent.");
    }

    if (opts.contains(CommonOptions.LBFGS)) {
      lbfgs = parser.accepts("lbfgs");
      lbfgsIterations = parser.accepts("lbfgsIterations",
          "Maximum number of iterations (passes over the data) for LBFGS.").
          withRequiredArg().ofType(Integer.class).defaultsTo(100);
      lbfgsHessianRank = parser.accepts("lbfgsHessianRank",
          "Rank (number of vectors) of LBFGS's inverse Hessian approximation.")
          .withRequiredArg().ofType(Integer.class).defaultsTo(30);
      lbfgsL2Regularization = parser.accepts("lbfgsL2Regularization",
          "L2 regularization imposed by LBFGS")
          .withRequiredArg().ofType(Double.class).defaultsTo(0.0);

      // Providing either of these options triggers the use of minibatch LBFGS
      lbfgsMinibatchIterations = parser.accepts("lbfgsMinibatchIterations",
          "If specified, run LBFGS on minibatches of the data with the specified number of iterations per minibatch.")
          .withRequiredArg().ofType(Integer.class).defaultsTo(-1);
      lbfgsMinibatchSize = parser.accepts("lbfgsMinibatchSize",
          "If specified, run LBFGS on minibatches of the data with the specified number of examples per minibatch.")
          .withRequiredArg().ofType(Integer.class).defaultsTo(-1);

      lbfgsAdaptiveMinibatches = parser.accepts("lbfgsAdaptiveMinibatches",
          "If given, LBFGS is run on minibatches of exponentially increasing size.");
    }

    if (opts.contains(CommonOptions.STOCHASTIC_GRADIENT) || opts.contains(CommonOptions.LBFGS)) {
      logInterval = parser.accepts("logInterval",
          "Number of training iterations between logging outputs.")
          .withRequiredArg().ofType(Integer.class).defaultsTo(1);
      
      logParametersInterval = parser.accepts("logParametersInterval",
          "Number of training iterations between serializing parameters to disk during training. "
          + "If unspecified, model parameters are not serialized to disk during training.")
          .withRequiredArg().ofType(Integer.class).defaultsTo(-1);
      logParametersDir = parser.accepts("logParametersDir", "Directory where serialized model "
          + "parameters are stored. Must be specified if logParametersInterval is specified.")
          .withRequiredArg().ofType(String.class);

      logBrief = parser.accepts("logBrief", "Hides training output.");
    }

    if (opts.contains(CommonOptions.MAP_REDUCE)) {
      mrMaxThreads = parser.accepts("maxThreads",
          "Maximum number of threads to use during parallel execution.")
          .withRequiredArg().ofType(Integer.class).defaultsTo(Runtime.getRuntime().availableProcessors());
      mrMaxBatchesPerThread = parser.accepts("maxBatchesPerThread",
          "Number of batches of items to create per thread.")
          .withRequiredArg().ofType(Integer.class).defaultsTo(20);
    }

    if (opts.contains(CommonOptions.FUNCTIONAL_GRADIENT_ASCENT)) {
      fgaIterations = parser.accepts("fgaIterations",
          "Number of iterations of functional gradient ascent to perform.").withRequiredArg()
          .ofType(Integer.class).defaultsTo(10);
      fgaBatchSize = parser.accepts("fgaBatchSize",
          "Number of examples to process before each functional gradient update. If not provided, use the entire data set.")
          .withRequiredArg().ofType(Integer.class);
      fgaInitialStep = parser.accepts("fgaInitialStepSize",
          "Initial step size for functional gradient ascent.")
          .withRequiredArg().ofType(Double.class).defaultsTo(1.0);
      fgaNoDecayStepSize = parser.accepts("fgaNoDecayStepSize",
          "Don't use a 1/sqrt(t) step size decay during functional gradient ascent.");
    }

    if (opts.contains(CommonOptions.REGRESSION_TREE)) {
      rtreeMaxDepth = parser.accepts("rtreeMaxDepth", "Maximum depth of trained regression trees")
          .withRequiredArg().ofType(Integer.class).required();
    }
  }

  /**
   * Initializes program state using any options processable by this
   * class.
   * 
   * @param options
   */
  private void processOptions(OptionSet options) {
    Pseudorandom.get().setSeed(options.valueOf(randomSeed));

    if (opts.contains(CommonOptions.MAP_REDUCE)) {
      MapReduceConfiguration.setMapReduceExecutor(new LocalMapReduceExecutor(
          options.valueOf(mrMaxThreads), options.valueOf(mrMaxBatchesPerThread)));
    }

    if (opts.contains(CommonOptions.STOCHASTIC_GRADIENT) || opts.contains(CommonOptions.LBFGS)) {
      LogFunction log = null;
      if (parsedOptions.has(logBrief)) {
        log = new NullLogFunction();
      } else {
         log = new DefaultLogFunction(parsedOptions.valueOf(logInterval), false,
             options.valueOf(logParametersInterval), options.valueOf(logParametersDir));
      }
      LogFunctions.setLogFunction(log);
    }
  }

  /**
   * Creates a {@code StochasticGradientTrainer} configured using the
   * provided options. In order to use this method, pass
   * {@link CommonOptions#STOCHASTIC_GRADIENT} to the constructor.
   * 
   * @return a stochastic gradient trainer configured using any
   * command-line options passed to the program
   */
  private StochasticGradientTrainer createStochasticGradientTrainer(int numExamples) {
    Preconditions.checkState(opts.contains(CommonOptions.STOCHASTIC_GRADIENT));

    long iterationsOption = parsedOptions.valueOf(sgdIterations);
    int batchSize = numExamples;
    if (parsedOptions.has(sgdBatchSize)) {
      batchSize = parsedOptions.valueOf(sgdBatchSize);
    }
    long numIterations = (int) Math.ceil(iterationsOption * numExamples / ((double) batchSize));
    double initialStepSize = parsedOptions.valueOf(sgdInitialStep);
    double l2Regularization = parsedOptions.valueOf(sgdL2Regularization);

    LogFunction log = LogFunctions.getLogFunction();
    StochasticGradientTrainer trainer = null;
    if (!parsedOptions.has(sgdAdagrad)) {
      trainer = StochasticGradientTrainer.createWithStochasticL2Regularization(
          numIterations, batchSize, initialStepSize, !parsedOptions.has(sgdNoDecayStepSize),
          !parsedOptions.has(sgdNoReturnAveragedParameters), l2Regularization,
          parsedOptions.valueOf(sgdRegularizationFrequency), log);
    } else {
      trainer = StochasticGradientTrainer.createAdagrad(
          numIterations, batchSize, initialStepSize, !parsedOptions.has(sgdNoDecayStepSize),
          !parsedOptions.has(sgdNoReturnAveragedParameters), l2Regularization,
          parsedOptions.valueOf(sgdRegularizationFrequency), log);
    }

    return trainer;
  }

  private GradientOptimizer createLbfgs(int numExamples) {
    Preconditions.checkState(opts.contains(CommonOptions.LBFGS));

    if (parsedOptions.has(lbfgsAdaptiveMinibatches)) {
      int lbfgsMinibatchSizeInt = parsedOptions.valueOf(lbfgsMinibatchSize);
      Preconditions.checkState(lbfgsMinibatchSizeInt != -1, "Must specify initial adaptive batch size using --lbfgsMinibatchSize");

      return MinibatchLbfgs.createAdaptiveSchedule(parsedOptions.valueOf(lbfgsHessianRank),
          parsedOptions.valueOf(lbfgsL2Regularization), numExamples, lbfgsMinibatchSizeInt,
          -1, LogFunctions.getLogFunction());
    }

    int lbfgsMinibatchSizeInt = parsedOptions.valueOf(lbfgsMinibatchSize);
    int lbfgsMinibatchIterationsInt = parsedOptions.valueOf(lbfgsMinibatchIterations);
    if (lbfgsMinibatchSizeInt != -1 && lbfgsMinibatchIterationsInt != -1) {
      // Using these options triggers minibatch LBFGS with a fixed
      // sized schedule.
      int batchIterations = (int) Math.ceil(((double) parsedOptions.valueOf(lbfgsIterations)) / lbfgsMinibatchIterationsInt);
      return MinibatchLbfgs.createFixedSchedule(parsedOptions.valueOf(lbfgsHessianRank),
          parsedOptions.valueOf(lbfgsL2Regularization), batchIterations,
          lbfgsMinibatchSizeInt, lbfgsMinibatchIterationsInt, LogFunctions.getLogFunction());
    } else if (lbfgsMinibatchIterationsInt == -1 && lbfgsMinibatchSizeInt == -1) {
      return new Lbfgs(parsedOptions.valueOf(lbfgsIterations), parsedOptions.valueOf(lbfgsHessianRank),
          parsedOptions.valueOf(lbfgsL2Regularization), LogFunctions.getLogFunction());
    }

    throw new UnsupportedOperationException(
        "Must specify both or neither of --lbfgsMinibatchIterations and --lbfgsMinibatchSize");
  }

  /**
   * Creates a gradient-based optimization algorithm based on the
   * given command-line parameters. To use this method, pass at least
   * one of {@link CommonOptions#STOCHASTIC_GRADIENT} or
   * {@link CommonOptions#LBFGS} to the constructor. This method
   * allows users to easily select different optimization algorithms
   * and parameterizations.
   * 
   * @param numExamples
   * @return
   */
  protected GradientOptimizer createGradientOptimizer(int numExamples) {
    if (opts.contains(CommonOptions.STOCHASTIC_GRADIENT) &&
        opts.contains(CommonOptions.LBFGS)) {
      if (parsedOptions.has(lbfgs)) {
        return createLbfgs(numExamples);
      } else {
        return createStochasticGradientTrainer(numExamples);
      }
    } else if (opts.contains(CommonOptions.STOCHASTIC_GRADIENT)) {
      return createStochasticGradientTrainer(numExamples);
    } else if (opts.contains(CommonOptions.LBFGS)) {
      return createLbfgs(numExamples);
    }

    throw new UnsupportedOperationException("To use createGradientOptimizer, the CLI constructor must specify STOCHASTIC_GRADIENT and/or LBFGS.");
  }

  protected FunctionalGradientAscent createFunctionalGradientAscent(int numExamples) {
    Preconditions.checkState(opts.contains(CommonOptions.FUNCTIONAL_GRADIENT_ASCENT));

    int batchSize = parsedOptions.has(fgaBatchSize) ? parsedOptions.valueOf(fgaBatchSize) : numExamples;
    int iterations = (int) Math.ceil(parsedOptions.valueOf(fgaIterations) * numExamples / ((double) batchSize));
    double initialStep = parsedOptions.valueOf(fgaInitialStep);
    boolean noDecay = parsedOptions.has(fgaNoDecayStepSize);
    LogFunction log = new DefaultLogFunction(1, false);

    return new FunctionalGradientAscent(iterations, batchSize, initialStep, !noDecay, log);
  }

  protected RegressionTreeTrainer createRegressionTreeTrainer() {
    Preconditions.checkState(opts.contains(CommonOptions.REGRESSION_TREE));

    return new RegressionTreeTrainer(parsedOptions.valueOf(rtreeMaxDepth));
  }
}
