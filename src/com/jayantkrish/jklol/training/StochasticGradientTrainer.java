package com.jayantkrish.jklol.training;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.evaluation.Example;
import com.jayantkrish.jklol.inference.MarginalCalculator;
import com.jayantkrish.jklol.inference.MarginalCalculator.ZeroProbabilityError;
import com.jayantkrish.jklol.inference.MarginalSet;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Trains the weights of a factor graph using stochastic gradient descent.
 */
public class StochasticGradientTrainer extends AbstractTrainer {

  private final MarginalCalculator marginalCalculator;
  private final int numIterations;
  private final LogFunction log;
  
  private final double stepSize;
  private final double l2Regularization;

  /**
   * @param inferenceEngine
   * @param numIterations
   * @param log
   * @param stepSize
   * @param l2Regularization valid only if {@code 0 <= l2Regularization < 1.0}.
   */
  public StochasticGradientTrainer(MarginalCalculator inferenceEngine, int numIterations,
      LogFunction log, double stepSize, double l2Regularization) {
    this.marginalCalculator = Preconditions.checkNotNull(inferenceEngine);
    this.numIterations = numIterations;
    this.log = Preconditions.checkNotNull(log);
    
    this.stepSize = stepSize;
    this.l2Regularization = l2Regularization;
    Preconditions.checkArgument(l2Regularization >= 0.0);
    Preconditions.checkArgument(l2Regularization * stepSize < 1);
  }

  public SufficientStatistics train(ParametricFactorGraph logLinearModel, SufficientStatistics initialParameters,
      Iterable<Example<DynamicAssignment, DynamicAssignment>> trainingData) {

    int iterationCount = 0;
    int searchErrors = 0;
    double gradientL2 = 0.0;
    for (int i = 0; i < numIterations; i++) {
      for (Example<DynamicAssignment, DynamicAssignment> trainingExample : trainingData) {
        searchErrors = 0;
        log.notifyIterationStart(iterationCount);

        try {
          log.startTimer("update_gradient");
          SufficientStatistics gradient = computeGradient(initialParameters, logLinearModel, trainingExample);
          // System.out.println(gradient);
          gradientL2 = gradient.getL2Norm();
          log.stopTimer("update_gradient");

          log.startTimer("parameter_update");
          double currentStepSize = stepSize / Math.sqrt(iterationCount + 2);
          System.out.println(currentStepSize);
          // Apply L2 regularization.
          initialParameters.multiply(1.0 - (currentStepSize * l2Regularization));
          initialParameters.increment(gradient, currentStepSize);
          // System.out.println(initialParameters);
          log.stopTimer("parameter_update");
        } catch (ZeroProbabilityError e) {
          searchErrors++;
        }

        log.logStatistic(iterationCount, "search errors", Integer.toString(searchErrors));
        log.logStatistic(iterationCount, "gradient l2 norm", Double.toString(gradientL2));
        log.notifyIterationEnd(iterationCount);
        iterationCount++;
      }
    }
    return initialParameters;
  }

  /*
   * Computes and returns an estimate of the gradient at {@code parameters}
   * based on {@code trainingExample}.
   */
  private SufficientStatistics computeGradient(SufficientStatistics parameters,
      ParametricFactorGraph logLinearModel, Example<DynamicAssignment, DynamicAssignment> dynamicExample) {
    // Instantiate any replicated factors, etc.
    log.startTimer("update_gradient/get_factor_graph_from_parameters");
    DynamicFactorGraph dynamicFactorGraph = logLinearModel.getFactorGraphFromParameters(parameters);
    FactorGraph factorGraph = dynamicFactorGraph.getFactorGraph(dynamicExample.getInput());
    Assignment input = dynamicFactorGraph.getVariables().toAssignment(dynamicExample.getInput());
    Assignment observed = dynamicFactorGraph.getVariables().toAssignment(
        dynamicExample.getOutput().union(dynamicExample.getInput()));

    log.stopTimer("update_gradient/get_factor_graph_from_parameters");
    log.log(input, factorGraph);
    log.log(observed, factorGraph);

    // The gradient is the conditional expected counts minus the unconditional
    // expected counts
    SufficientStatistics gradient = logLinearModel.getNewSufficientStatistics();

    log.startTimer("update_gradient/input_marginal");
    // Compute the second term of the gradient, the unconditional expected
    // feature counts
    FactorGraph inputFactorGraph = factorGraph.conditional(input);
    MarginalSet inputMarginals = marginalCalculator.computeMarginals(inputFactorGraph);
    log.stopTimer("update_gradient/input_marginal");

    log.startTimer("update_gradient/output_marginal");
    // Compute the first term of the gradient, the model expectations
    // conditioned on the training example.
    FactorGraph outputFactorGraph = factorGraph.conditional(observed);
    MarginalSet outputMarginals = marginalCalculator.computeMarginals(
        outputFactorGraph);
    log.stopTimer("update_gradient/output_marginal");

    // Perform the gradient update. Note that this occurs after both marginal
    // calculations, since the marginal calculations may throw ZeroProbabilityErrors
    // (if inference in the graphical model fails.)
    log.startTimer("update_gradient/increment");
    logLinearModel.incrementSufficientStatistics(gradient, inputMarginals, -1.0);
    logLinearModel.incrementSufficientStatistics(gradient, outputMarginals, 1.0);
    log.stopTimer("update_gradient/increment");

    return gradient;
  }
}