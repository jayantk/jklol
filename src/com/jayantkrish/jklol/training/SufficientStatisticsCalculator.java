package com.jayantkrish.jklol.training;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.google.common.base.Supplier;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.inference.MarginalCalculator;
import com.jayantkrish.jklol.inference.MarginalSet;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Calculates the sufficient statistics for a {@link FactorGraph}, conditioned
 * on various {@code Assignment}s (training examples). This class takes a batch
 * of training examples and computes the sum of the sufficient statistics for
 * each example. This is a useful subroutine in many training algorithms.
 * 
 * <p>
 * The computation for each batch is parallelized to improve training speed.
 * There is some overhead in parallelization; larger batch sizes amortize this
 * overhead more effectively.
 * 
 * @author jayantk
 */
public class SufficientStatisticsCalculator {

  private final Supplier<MarginalCalculator> marginalCalculatorSupplier;
  private final int numConcurrent;
  private final ExecutorService executor;

  /**
   * Creates a {@code SufficientStatisticsCalculator} which computes marginals
   * using the calculators retrieved from {@code marginalCalculatorSupplier}.
   * {@code numConcurrent} marginals are computed simultaneously.
   * 
   * @param marginalCalculatorSupplier
   * @param numConcurrent
   */
  public SufficientStatisticsCalculator(Supplier<MarginalCalculator> marginalCalculatorSupplier,
      int numConcurrent) {
    this.marginalCalculatorSupplier = marginalCalculatorSupplier;
    this.numConcurrent = numConcurrent;
    this.executor = Executors.newFixedThreadPool(numConcurrent);
  }

  /**
   * Returns the sum of the {@code SufficientStatistics} for {@code assignments}
   * . This method conditions on each assignment, computes its corresponding
   * marginal distribution, then converts it into sufficient statistics for
   * {@code factorGraph}.
   * 
   * <p>
   * {@code assignments} does not need to be safe for concurrent access as its
   * contents is internally copied to a thread-safe data structure.
   * 
   * @param factorGraph
   * @param bayesNet
   * @param assignments
   * @param log
   * @return
   */
  public BatchStatistics computeSufficientStatistics(FactorGraph factorGraph,
      ParametricFactorGraph bayesNet, List<Assignment> assignments, LogFunction log) {

    List<BatchCalculator> batches = Lists.newArrayList();
    int batchSize = (int) Math.ceil(((double) assignments.size()) / numConcurrent);
    for (int i = 0; i < numConcurrent; i++) {
      // The logger might not be safe for concurrent access, so only
      // pass it to one of the update processes.
      LogFunction logFn = (i == 0) ? log : new NullLogFunction();

      // Note that both the start and end index can be larger than
      // assignments.size(). For example, the start index is larger if
      // the number of concurrent processors is greater than the number of
      // examples in the batch.
      List<Assignment> assignmentBatch = Lists.newArrayList(assignments
          .subList(Math.min(i * batchSize, assignments.size()),
              Math.min((i + 1) * batchSize, assignments.size())));

      batches.add(new BatchCalculator(factorGraph, bayesNet, marginalCalculatorSupplier.get(),
          assignmentBatch, logFn));
    }

    BatchStatistics statistics = new BatchStatistics(bayesNet.getNewSufficientStatistics(), 0.0, 0);
    try {
      List<Future<BatchStatistics>> results = executor.invokeAll(batches);
      for (Future<BatchStatistics> result : results) {
        statistics.increment(result.get());
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    } catch (ExecutionException e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
    return statistics;
  }

  /**
   * Calculates the sufficient statistics for a given batch of
   * {@code Assignment}s. Used for parallelizing the computation of sufficient
   * statistics using {@code Future}s.
   * 
   * @author jayantk
   */
  private static class BatchCalculator implements Callable<BatchStatistics> {

    private final FactorGraph factorGraph;
    private final ParametricFactorGraph bayesNet;

    private final MarginalCalculator marginalCalculator;
    private final List<Assignment> trainingData;
    private final LogFunction logFn;

    public BatchCalculator(FactorGraph factorGraph, ParametricFactorGraph bayesNet,
        MarginalCalculator marginalCalculator, List<Assignment> trainingData,
        LogFunction logFn) {
      this.factorGraph = factorGraph;
      this.bayesNet = bayesNet;
      this.marginalCalculator = marginalCalculator;
      this.trainingData = trainingData;
      this.logFn = logFn;
    }

    public BatchStatistics call() {
      double sumLoglikelihood = 0.0;
      SufficientStatistics statistics = bayesNet.getNewSufficientStatistics();
      for (Assignment assignment : trainingData) {
        logFn.log(assignment, factorGraph);
        MarginalSet marginals = marginalCalculator.computeMarginals(factorGraph, assignment);
        statistics.increment(bayesNet.computeSufficientStatistics(marginals, 1.0), 1.0);

        sumLoglikelihood += Math.log(marginals.getPartitionFunction());
      }

      return new BatchStatistics(statistics, sumLoglikelihood, trainingData.size());
    }
  }

  /**
   * Simple data structure for holding the result of a batch computation.
   * 
   * @author jayantk
   */
  public static class BatchStatistics {
    private SufficientStatistics statistics;
    private double loglikelihood;
    private int numExamples;

    public BatchStatistics(SufficientStatistics statistics, double loglikelihood, int numExamples) {
      this.statistics = statistics;
      this.loglikelihood = loglikelihood;
      this.numExamples = numExamples;
    }

    public SufficientStatistics getStatistics() {
      return statistics;
    }

    /**
     * Gets the sum of the unnormalized loglikelihood of each training example
     * in the batch. The actual loglikelihood of the training data is equal to
     * {@code this.getLoglikelihood() - this.getNumExamples() * logPartitionFunction}
     * , where {@code logPartitionFunction} is the log partition function of the
     * factor graph the statistics were computed on.
     * 
     * @return
     */
    public double getLoglikelihood() {
      return loglikelihood;
    }

    public int getNumExamples() {
      return numExamples;
    }

    /**
     * Adds the statistics in {@code other} to {@code this}.
     * 
     * @param other
     */
    public void increment(BatchStatistics other) {
      statistics.increment(other.statistics, 1.0);
      loglikelihood += other.loglikelihood;
      numExamples += other.numExamples;
    }
  }
}
