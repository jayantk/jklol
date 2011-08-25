package com.jayantkrish.jklol.training;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.inference.MarginalCalculator;
import com.jayantkrish.jklol.inference.MarginalSet;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.bayesnet.BayesNet;
import com.jayantkrish.jklol.models.bayesnet.CptFactor;
import com.jayantkrish.jklol.util.Assignment;
import com.sun.istack.internal.Nullable;

/**
 * Train the weights of a BayesNet using stepwise EM, an online variant of EM.
 */
public class StepwiseEMTrainer {

  private final int batchSize;
  private final int numIterations;
  private final double smoothing;
  private final double decayRate;
  private final LogFunction log;
  private final Supplier<MarginalCalculator> inferenceEngineSupplier;

  // These parameters control the amount of parallel execution.
  // The sufficient statistics of each batch are run as numConcurrent
  // parallel tasks, which are evaluated using executor.
  private final int numConcurrent;
  private final ExecutorService executor;

  // These variables get initialized during each call to train()
  private double totalDecay;
  private BayesNet bn;
  private List<CptFactor> factorsToUpdate;
  private Factor[][] storedMarginals;
  private double[] storedPartitionFunctions;
  private Assignment[] trainingDataArray;

  /**
   * Creates a trainer which performs {@code numIterations} of stepwise EM
   * training. Each iteration of training processes a series training example
   * batches; after each batch, the model parameters are updated.
   * {@code batchSize} controls the number of training examples in each batch.
   * 
   * <p>
   * This class supports parallel training. Each batch is split into
   * {@code numConcurrent} mini-batches which are processed concurrently.
   * 
   * @param numIterations number of iterations to train for.
   * @param batchSize number of examples to process between parameter updates.
   * @param smoothing added to initial parameter estimates; note that the
   * smoothing value decays as the number of iterations increases, and the
   * resulting parameters will be closer to an unsmoothed estimate.
   * @param decayRate controls how fast old statistics are removed from the
   * model. Smaller decayRates cause old statistics to be forgotten faster. Must
   * satisfy {@code 0.5 < decayRate <= 1}.
   * @param inferenceEngineSupplier gets the inference procedure which computes
   * marginals for training.
   * @param numConcurrent number of mini-batches
   * @param log monitors training progress, if provided.
   */
  public StepwiseEMTrainer(int numIterations, int batchSize, double smoothing,
      double decayRate, Supplier<MarginalCalculator> inferenceEngineSupplier, int numConcurrent,
      @Nullable LogFunction log) {
    Preconditions.checkArgument(0.5 < decayRate && decayRate <= 1.0);

    this.numIterations = numIterations;
    this.batchSize = batchSize;
    this.smoothing = smoothing;
    this.decayRate = decayRate;
    this.inferenceEngineSupplier = inferenceEngineSupplier;
    this.log = log;

    this.numConcurrent = numConcurrent;
    this.executor = Executors.newFixedThreadPool(numConcurrent);
  }

  public void train(BayesNet bn, List<Assignment> trainingData) {
    initializeCpts(bn);

    // Initialize state variables, which are used in updateBatchStatistics() and
    // updateParameters()
    this.bn = bn;
    factorsToUpdate = bn.getCptFactors();
    storedMarginals = new Factor[factorsToUpdate.size()][batchSize];
    storedPartitionFunctions = new double[batchSize];
    totalDecay = 1.0;
    trainingDataArray = trainingData.toArray(new Assignment[0]);

    int numUpdates = 0;

    Collections.shuffle(trainingData);

    for (int i = 0; i < numIterations; i++) {
      System.out.println(i);
      if (log != null) {
        log.notifyIterationStart(i);
      }
      int numBatches = (int) Math.ceil(((double) trainingData.size()) / batchSize);
      for (int j = 0; j < numBatches; j++) {
        System.out.println(i + " : " + j);
        int numExamples = updateBatchStatistics(i, j * batchSize, batchSize);
        updateParameters(numExamples, numUpdates);
        numUpdates++;
      }
      if (log != null) {
        log.notifyIterationEnd(i);
      }
    }
  }

  private int updateBatchStatistics(int iterationNum, int startIndex, int batchSize) {

    List<BatchUpdater> batches = Lists.newArrayList();
    for (int i = 0; i < numConcurrent; i++) {
      batches.add(new BatchUpdater(trainingDataArray, startIndex,
          startIndex + batchSize, i, numConcurrent));
    }

    try {
      List<Future<Boolean>> results = executor.invokeAll(batches);
      for (Future<Boolean> result : results) {
        result.get();
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    } catch (ExecutionException e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }

    if (startIndex + batchSize > trainingDataArray.length) {
      return trainingDataArray.length - startIndex;
    } else {
      return batchSize;
    }
  }

  private void updateParameters(int numValidEntries, int numUpdates) {
    // Instead of multiplying the sufficient statistics (dense update)
    // use a sparse update which simply increases the weight of the added
    // marginal.
    double batchDecayParam = Math.pow((numUpdates + 2), -1.0 * decayRate);
    totalDecay *= (1.0 - batchDecayParam);

    double batchMultiplier = batchDecayParam / totalDecay;
    for (int i = 0; i < factorsToUpdate.size(); i++) {
      CptFactor factor = factorsToUpdate.get(i);
      for (int j = 0; j < numValidEntries; j++) {
        System.out.println(i + ":" + j + " : " + storedMarginals[i][j]);
        System.out.println(i + ":" + j + " : " + storedPartitionFunctions[j]);
        
        factor.incrementOutcomeCount(storedMarginals[i][j], batchMultiplier,
            storedPartitionFunctions[j]);
      }
    }
  }

  private void initializeCpts(BayesNet bn) {
    // Set all CPT statistics to the smoothing value
    for (CptFactor cptFactor : bn.getCptFactors()) {
      cptFactor.clearCpt();
      cptFactor.addUniformSmoothing(smoothing);
    }
  }

  private class BatchUpdater implements Callable<Boolean> {

    private final Assignment[] trainingData;
    private final int startIndex;
    private final int endIndex;
    private final int offset;
    private final int increment;

    public BatchUpdater(Assignment[] trainingData, int startIndex, int endIndex, int offset,
        int increment) {
      this.trainingData = trainingData;
      this.startIndex = startIndex;
      this.endIndex = endIndex;
      this.offset = offset;
      this.increment = increment;
    }

    public Boolean call() {
      MarginalCalculator inferenceEngine = inferenceEngineSupplier.get();
      inferenceEngine.setFactorGraph(bn);

      for (int i = startIndex + offset; i < Math.min(endIndex, trainingData.length); i += increment) {
        Assignment trainingExample = trainingData[i];
        if (log != null) {
          log.log(0, i, trainingExample, bn);
        }

        // TODO(jayantk): Reusing inferenceEngine like this is going to be
        // trouble.
        MarginalSet marginals = inferenceEngine.computeMarginals(trainingExample);
        storedPartitionFunctions[(i - startIndex)] = marginals.getPartitionFunction();
        for (int k = 0; k < factorsToUpdate.size(); k++) {
          CptFactor cptFactor = factorsToUpdate.get(k);
          Factor marginal = marginals.getMarginal(cptFactor.getVars().getVariableNums());

          storedMarginals[k][(i - startIndex)] = marginal;

          if (log != null) {
            log.log(0, i, cptFactor, marginal, bn);
          }
        }
      }
      return true;
    }
  }
}