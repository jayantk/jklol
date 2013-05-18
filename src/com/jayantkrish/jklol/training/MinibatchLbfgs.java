package com.jayantkrish.jklol.training;

import java.util.Iterator;
import java.util.List;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

public class MinibatchLbfgs {

  private final int maxIterations;
  private final int numVectorsInApproximation;
  private final double l2Regularization;
    private final int minibatchSize;
    private final int iterationsPerMinibatch;

  private final LogFunction log;

  public MinibatchLbfgs(int maxIterations, int numVectorsInApproximation,
			double l2Regularization, int minibatchSize, int iterationsPerMinibatch,
			LogFunction log) {
    this.maxIterations = maxIterations;
    this.numVectorsInApproximation = numVectorsInApproximation;
    this.l2Regularization = l2Regularization;
    this.minibatchSize = minibatchSize;
    this.iterationsPerMinibatch = iterationsPerMinibatch;
    this.log = log;
  }

    public <M, E> SufficientStatistics train(GradientOracle<M, E> oracle,
					     SufficientStatistics initialParameters, Iterable<E> trainingData) {
	Iterator<E> cycledTrainingData = Iterators.cycle(trainingData);
	int batchIterations = (int) Math.ceil(((double) maxIterations) / iterationsPerMinibatch);
	SufficientStatistics parameters = initialParameters;
	for (int i = 0; i < batchIterations; i++) {
	    Lbfgs lbfgs = new Lbfgs(iterationsPerMinibatch, numVectorsInApproximation, 
				    l2Regularization, log);
	    List<E> batchData = getBatch(cycledTrainingData, minibatchSize);
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