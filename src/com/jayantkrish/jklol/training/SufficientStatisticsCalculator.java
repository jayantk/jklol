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
import com.jayantkrish.jklol.models.bayesnet.BayesNet;
import com.jayantkrish.jklol.models.bayesnet.SufficientStatistics;
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
   * {@code assignments} should be safe for concurrent access, but does not need
   * to support concurrent modification. An {@code ArrayList} works. For
   * processing batches, use {@code List.subList}.
   * 
   * @param factorGraph
   * @param assignments
   * @param log
   * @return
   */
  public SufficientStatistics computeSufficientStatistics(BayesNet factorGraph,
      List<Assignment> assignments, LogFunction log) {

    List<BatchCalculator> batches = Lists.newArrayList();
    int batchSize = (int) Math.ceil(((double) assignments.size()) / numConcurrent);
    for (int i = 0; i < numConcurrent; i++) {
      // The logger might not be safe for concurrent access, so only
      // pass it to one of the update processes.
      LogFunction logFn = (i == 0) ? log : null;

      // Note that both the start and end index can be larger than
      // assignments.size(). For example, the start index is larger if
      // the number of concurrent processors is greater than the number of
      // examples in the batch.
      List<Assignment> assignmentBatch = assignments.subList(Math.min(i * batchSize, assignments.size()),
          Math.min((i + 1) * batchSize, assignments.size()));

      batches.add(new BatchCalculator(factorGraph, marginalCalculatorSupplier.get(),
          assignmentBatch, logFn));
    }

    SufficientStatistics statistics = factorGraph.getNewSufficientStatistics(); 
    try {
      List<Future<SufficientStatistics>> results = executor.invokeAll(batches);
      for (Future<SufficientStatistics> result : results) {
        statistics.increment(result.get(), 1.0);
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
  private class BatchCalculator implements Callable<SufficientStatistics> {

    private final MarginalCalculator marginalCalculator;
    private final BayesNet factorGraph;
    private final List<Assignment> trainingData;
    private final LogFunction logFn;

    public BatchCalculator(BayesNet factorGraph, MarginalCalculator marginalCalculator,
        List<Assignment> trainingData, LogFunction logFn) {
      this.marginalCalculator = marginalCalculator;
      this.factorGraph = factorGraph;
      this.trainingData = trainingData;
      this.logFn = logFn;
    }

    public SufficientStatistics call() {
      SufficientStatistics startingStatistics = factorGraph.getNewSufficientStatistics();

      for (Assignment assignment : trainingData) {
        if (logFn != null) {
          logFn.log(assignment, factorGraph);
        }

        MarginalSet marginals = marginalCalculator.computeMarginals(factorGraph, assignment);
        startingStatistics.increment(factorGraph.computeSufficientStatistics(marginals, 1.0), 1.0);
      }

      return startingStatistics;
    }
  }
}
