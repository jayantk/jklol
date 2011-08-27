package com.jayantkrish.jklol.training;

import java.util.Collections;
import java.util.List;

import com.jayantkrish.jklol.inference.MarginalCalculator;
import com.jayantkrish.jklol.inference.MarginalSet;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.bayesnet.BayesNet;
import com.jayantkrish.jklol.models.bayesnet.SufficientStatistics;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Train the weights of a factor graph using incremental EM. (Incremental EM is
 * an online variant of EM.)
 */
public class IncrementalEMTrainer {

  private MarginalCalculator inferenceEngine;
  private int numIterations;
  private LogFunction log;

  public IncrementalEMTrainer(int numIterations, MarginalCalculator inferenceEngine) {
    this.numIterations = numIterations;
    this.inferenceEngine = inferenceEngine;
    this.log = null;
  }

  public IncrementalEMTrainer(int numIterations, MarginalCalculator inferenceEngine, 
      LogFunction log) {
    this.numIterations = numIterations;
    this.inferenceEngine = inferenceEngine;
    this.log = log;
  }

  /**
   * Trains {@code bn} using {@code trainingData}. After this method returns,
   * the parameters of {@code bn} will be updated to the trained values.
   * 
   * <p>
   * The current parameters of {@code bn} are used as the starting point for
   * training. A reasonable starting point is the uniform distribution (add-one
   * smoothing). To use this setting, run
   * {@code bn.setCurrentParameters(bn.getNewSufficientStatistics()); 
   * bn.getParameters().increment(1);}. Note that incremental EM will not forget
   * the smoothing.
   * 
   * @param bn
   * @param trainingData
   */
  public void train(BayesNet bn, List<Assignment> trainingData) {
    SufficientStatistics[] previousIterationStatistics = new SufficientStatistics[trainingData.size()];

    Collections.shuffle(trainingData);
    for (int i = 0; i < numIterations; i++) {
      if (log != null) {
        log.notifyIterationStart(i);
      }
      for (int j = 0; j < trainingData.size(); j++) {
        Assignment trainingExample = trainingData.get(j);
        if (log != null) {
          log.log(i, j, trainingExample, bn);
        }

        if (i > 0) {
          // Subtract out old statistics if they exist.
          bn.getCurrentParameters().increment(previousIterationStatistics[j], -1.0);
        }
        // Update new sufficient statistics
        MarginalSet marginals = inferenceEngine.computeMarginals(bn, trainingExample);
        SufficientStatistics exampleStatistics = bn.computeSufficientStatistics(marginals, 1.0);
        previousIterationStatistics[j] = exampleStatistics;
        bn.getCurrentParameters().increment(exampleStatistics, 1.0);

        if (log != null) {
          for (Factor factor : bn.getCptFactors()) {
            log.log(i, j, factor, marginals.getMarginal(factor.getVars().getVariableNums()), bn);
          }
        }
      }
      if (log != null) {
        log.notifyIterationEnd(i);
      }
    }
  }
}