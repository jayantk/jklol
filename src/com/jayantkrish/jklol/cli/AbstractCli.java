package com.jayantkrish.jklol.cli;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.boost.FunctionalGradientAscent;
import com.jayantkrish.jklol.ccg.CcgFeatureFactory;
import com.jayantkrish.jklol.ccg.CcgRuleSchema;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.dtree.RegressionTreeTrainer;
import com.jayantkrish.jklol.parallel.LocalMapReduceExecutor;
import com.jayantkrish.jklol.parallel.MapReduceConfiguration;
import com.jayantkrish.jklol.training.DefaultLogFunction;
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.training.NullLogFunction;
import com.jayantkrish.jklol.training.StochasticGradientTrainer;
import com.jayantkrish.jklol.util.IoUtils;
import com.jayantkrish.jklol.util.Pseudorandom;

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
     * Enables parallelization options configuring the execution of
     * embarassingly parallel tasks. These options set the default
     * {@code MapReduceExecutor} used by the program. For example,
     * these options include the maximum number of threads to use.
     */
    MAP_REDUCE,
    /**
     * Enables options for constructing a CCG parser, e.g., by
     * providing a lexicon and CCG rules.
     */
    PARAMETRIC_CCG_PARSER,
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

  // Stochastic gradient options.
  protected OptionSpec<Integer> sgdIterations;
  protected OptionSpec<Integer> sgdBatchSize;
  protected OptionSpec<Integer> sgdLogInterval;
  protected OptionSpec<Double> sgdInitialStep;
  protected OptionSpec<Void> sgdNoDecayStepSize;
  protected OptionSpec<Double> sgdL2Regularization;
  protected OptionSpec<Void> sgdBrief;

  // Map reduce options.
  protected OptionSpec<Integer> mrMaxThreads;
  protected OptionSpec<Integer> mrMaxBatchesPerThread;

  // CCG parser options
  protected OptionSpec<String> ccgLexicon;
  protected OptionSpec<String> ccgRules;
  protected OptionSpec<String> ccgDependencyFeatures;
  protected OptionSpec<Void> ccgApplicationOnly;
  
  // Functional gradient ascent options
  protected OptionSpec<Integer> fgaIterations;
  protected OptionSpec<Double> fgaInitialStep;
  protected OptionSpec<Void> fgaNoDecayStepSize;
  
  // Regression tree options
  protected OptionSpec<Integer> rtreeMaxDepth;
  
  /**
   * Creates a command line program that accepts the specified set of
   * options.
   * 
   * @param opts
   *          any optional option sets to accept
   */
  public AbstractCli(CommonOptions... opts) {
    this.opts = Sets.newHashSet(opts);
  }

  /**
   * Runs the program, parsing any options from {@code args}.
   * 
   * @param args
   *          arguments to the program, in the same format as provided
   *          by {@code main}.
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
      System.out.println(errorMessage);
      System.out.println("Try --help for more information about options.");
      System.exit(0);
    }

    if (printHelp || parsedOptions.has(helpOpt)) {
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
    long startTime = System.currentTimeMillis();
    processOptions(parsedOptions);
    run(parsedOptions);
    long endTime = System.currentTimeMillis();
    double timeElapsed = ((double) (endTime - startTime)) / 1000;
    System.out.println("Total time elapsed: " + timeElapsed + " seconds");

    System.exit(0);
  }

  /**
   * Adds subclass-specific options to {@code parser}. Subclasses must
   * implement this method in order to accept class-specific options.
   * 
   * @param parser
   *          option parser to which additional command-line options
   *          should be added.
   */
  public abstract void initializeOptions(OptionParser parser);

  /**
   * Runs the program using parsed {@code options}.
   * 
   * @param options
   *          option values passed to the program
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

    if (opts.contains(CommonOptions.STOCHASTIC_GRADIENT)) {
      sgdIterations = parser.accepts("iterations",
          "Number of iterations (passes over the data) for stochastic gradient descent.").
          withRequiredArg().ofType(Integer.class).defaultsTo(10);
      sgdBatchSize = parser.accepts("batchSize",
          "Minibatch size, i.e., the number of examples processed per gradient computation. If unspecified, defaults to using the entire data set (gradient descent).")
          .withRequiredArg().ofType(Integer.class);
      sgdLogInterval = parser.accepts("logInterval",
          "Number of iterations of stochastic gradient training between logging outputs.")
          .withRequiredArg().ofType(Integer.class).defaultsTo(1);
      sgdInitialStep = parser.accepts("initialStepSize",
          "Initial step size for stochastic gradient descent.")
          .withRequiredArg().ofType(Double.class).defaultsTo(1.0);
      sgdNoDecayStepSize = parser.accepts("noDecayStepSize",
          "Don't use a 1/sqrt(t) step size decay during stochastic gradient descent.");
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

    if (opts.contains(CommonOptions.PARAMETRIC_CCG_PARSER)) {
      ccgLexicon = parser.accepts("lexicon",
          "The CCG lexicon defining the grammar to use.").withRequiredArg()
          .ofType(String.class).required();
      // Optional options
      ccgRules = parser.accepts("rules",
          "Binary and unary rules to use during CCG parsing, in addition to function application and composition.")
          .withRequiredArg().ofType(String.class);
      ccgApplicationOnly = parser.accepts("applicationOnly",
          "Use only function application during parsing, i.e., no composition.");
    }
    
    if (opts.contains(CommonOptions.FUNCTIONAL_GRADIENT_ASCENT)) {
      fgaIterations = parser.accepts("fgaIterations",
          "Number of iterations of functional gradient ascent to perform.").withRequiredArg()
          .ofType(Integer.class).defaultsTo(10);
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
  }

  /**
   * Creates a {@code StochasticGradientTrainer} configured using the
   * provided options. In order to use this method, pass
   * {@link CommonOptions#STOCHASTIC_GRADIENT} to the constructor.
   * 
   * @return a stochastic gradient trainer configured using any
   *         command-line options passed to the program
   */
  protected StochasticGradientTrainer createStochasticGradientTrainer(int numExamples) {
    Preconditions.checkState(opts.contains(CommonOptions.STOCHASTIC_GRADIENT));

    int iterationsOption = parsedOptions.valueOf(sgdIterations);
    int batchSize = numExamples;
    if (parsedOptions.has(sgdBatchSize)) {
	batchSize = parsedOptions.valueOf(sgdBatchSize);
    }
    int numIterations = (int) Math.ceil(iterationsOption * numExamples / ((double) batchSize));
    double initialStepSize = parsedOptions.valueOf(sgdInitialStep);
    double l2Regularization = parsedOptions.valueOf(sgdL2Regularization);
    boolean brief = parsedOptions.has(sgdBrief);

    LogFunction log = (brief ? new NullLogFunction()
        : new DefaultLogFunction(parsedOptions.valueOf(sgdLogInterval), false));
    StochasticGradientTrainer trainer = StochasticGradientTrainer.createWithL2Regularization(
        numIterations, batchSize, initialStepSize, !parsedOptions.has(sgdNoDecayStepSize),
        l2Regularization, log);

    return trainer;
  }

  protected ParametricCcgParser createCcgParser(Set<String> posTagSet, Set<CcgRuleSchema> rules,
      CcgFeatureFactory featureFactory) {
    // Read in the lexicon to instantiate the model.
    List<String> lexiconEntries = IoUtils.readLines(parsedOptions.valueOf(ccgLexicon));
    List<String> ruleEntries = parsedOptions.has(ccgRules) ? IoUtils.readLines(parsedOptions.valueOf(ccgRules))
        : Collections.<String> emptyList();
    return ParametricCcgParser.parseFromLexicon(lexiconEntries, ruleEntries, featureFactory,
        posTagSet, !parsedOptions.has(ccgApplicationOnly), rules, false);
  }
  
  protected FunctionalGradientAscent createFunctionalGradientAscent(int numExamples) {
    Preconditions.checkState(opts.contains(CommonOptions.FUNCTIONAL_GRADIENT_ASCENT));
    
    int iterations = parsedOptions.valueOf(fgaIterations);
    double initialStep = parsedOptions.valueOf(fgaInitialStep);
    boolean noDecay = parsedOptions.has(fgaNoDecayStepSize);
    LogFunction log = new DefaultLogFunction(1, false);
    
    return new FunctionalGradientAscent(iterations, numExamples, initialStep, !noDecay, log);
  }
  
  protected RegressionTreeTrainer createRegressionTreeTrainer() {
    Preconditions.checkState(opts.contains(CommonOptions.REGRESSION_TREE));

    return new RegressionTreeTrainer(parsedOptions.valueOf(rtreeMaxDepth));
  }
}
