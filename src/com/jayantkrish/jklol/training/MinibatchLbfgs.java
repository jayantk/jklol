package com.jayantkrish.jklol.training;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

/**
 * Version of LBFGS that optimizes parameters on minibatches sampled 
 * from the data.
 * 
 * @author jayantk
 */
public class MinibatchLbfgs implements GradientOptimizer {

  private final int numVectorsInApproximation;
  private final double l2Regularization;

  private final int[] minibatchSizeSchedule;
  private final int[] maxIterationsPerMinibatch;

  private final LogFunction log;

  public MinibatchLbfgs(int numVectorsInApproximation, double l2Regularization,
      int[] minibatchSizeSchedule, int[] maxIterationsPerMinibatch, LogFunction log) {
    this.numVectorsInApproximation = numVectorsInApproximation;
    this.l2Regularization = l2Regularization;
    this.minibatchSizeSchedule = minibatchSizeSchedule;
    this.maxIterationsPerMinibatch = maxIterationsPerMinibatch;
    this.log = log;
  }
  
  /**
   * Creates a minibatch LBFGS trainer that iterates over {@code batchIterations}
   * minibatches, where each batch contains {@code examplesPerMinibatch} examples 
   * and is optimized with LBFGS for {@code iterationsPerMinibatch}.
   *   
   * @param numVectorsInApproximation
   * @param l2Regularization
   * @param batchIterations
   * @param examplesPerMinibatch
   * @param iterationsPerMinibatch
   * @param log
   * @return
   */
  public static MinibatchLbfgs createFixedSchedule(int numVectorsInApproximation,
      double l2Regularization, int batchIterations, int examplesPerMinibatch,
      int iterationsPerMinibatch, LogFunction log) {
    int[] minibatchSizeSchedule = new int[batchIterations];
    int[] maxIterationsPerMinibatch = new int[batchIterations];
    
    Arrays.fill(minibatchSizeSchedule, examplesPerMinibatch);
    Arrays.fill(maxIterationsPerMinibatch, iterationsPerMinibatch);
    
    return new MinibatchLbfgs(numVectorsInApproximation, l2Regularization, minibatchSizeSchedule,
        maxIterationsPerMinibatch, log);
  }
  
  /**
   * Creates a minibatch LBFGS trainer that adaptively increases the number of 
   * examples in each minibatch as training progresses. Each iteration doubles 
   * the number of examples in the batch, with the first batch containing 
   * {@code initialMinibatchSize} examples. Each minibatch is optimized with
   * LBFGS for {@code iterationsPerMinibatch} iterations.
   * 
   * @param numVectorsInApproximation
   * @param l2Regularization
   * @param numExamples
   * @param initialMinibatchSize
   * @param iterationsPerMinibatch
   * @param log
   * @return
   */
  public static MinibatchLbfgs createAdaptiveSchedule(int numVectorsInApproximation,
      double l2Regularization, int numExamples, int initialMinibatchSize,
      int iterationsPerMinibatch, LogFunction log) {
    int numBatches = (int) Math.ceil(Math.log(((double) numExamples) / initialMinibatchSize) / Math.log(2));
    int[] minibatchSize = new int[numBatches];
    int[] maxIterationsPerMinibatch = new int[iterationsPerMinibatch];
    
    minibatchSize[0] = initialMinibatchSize;
    for (int i = 1; i < numBatches; i++) {
      minibatchSize[i] = minibatchSize[i -1] * 2;
    }
    minibatchSize[numBatches - 1] = numExamples;
    
    Arrays.fill(maxIterationsPerMinibatch, iterationsPerMinibatch);
    
    return new MinibatchLbfgs(numVectorsInApproximation, l2Regularization, minibatchSize,
        maxIterationsPerMinibatch, log);
  }

  @Override
  public <M, E, T extends E> SufficientStatistics train(GradientOracle<M, E> oracle,
      SufficientStatistics initialParameters, Iterable<T> trainingData) {
    Iterator<T> cycledTrainingData = Iterators.cycle(trainingData);
    SufficientStatistics parameters = initialParameters;
    for (int i = 0; i < minibatchSizeSchedule.length; i++) {
      Lbfgs lbfgs = new Lbfgs(maxIterationsPerMinibatch[i], numVectorsInApproximation, 
          l2Regularization, log);
      List<T> batchData = getBatch(cycledTrainingData, minibatchSizeSchedule[i]);
      try {
        parameters = lbfgs.train(oracle, parameters, batchData);
      } catch (LbfgsConvergenceError error) {
        log.logMessage("L-BFGS Convergence Failed. Moving to next minibatch");
        parameters = error.getFinalParameters();
      }
    }
    return parameters;
  }

  private <S> List<S> getBatch(Iterator<S> trainingData, int batchSize) {
    List<S> batchData = Lists.newArrayListWithCapacity(batchSize);
    for (int i = 0; i < batchSize && trainingData.hasNext(); i++) {
      batchData.add(trainingData.next());
    }
    return batchData;
  }
}