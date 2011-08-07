package com.jayantkrish.jklol.evaluation;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/**
 * Implementations of common {@link Evaluation} methods. 
 * @author jayant
 *
 * @param <I>
 * @param <O>
 */
public abstract class AbstractEvaluation<I, O> implements Evaluation<I, O> {

	@Override
    public void evaluateLoss(PredictorTrainer<I, O> predictorTrainer, 
    		LossFunction<I, O> ... lossFunctions) {
		Preconditions.checkNotNull(predictorTrainer);
		Preconditions.checkNotNull(lossFunctions);
		
		evaluateLoss(predictorTrainer, Arrays.asList(lossFunctions));
	}
	
	@Override
    public void evaluateLoss(PredictorTrainer<I, O> predictorTrainer, 
    		LossFunction<I, O> lossFunction) {
		Preconditions.checkNotNull(predictorTrainer);
		Preconditions.checkNotNull(lossFunction);
		
		List<LossFunction<I, O>> lossFunctions = Lists.newArrayList();
		lossFunctions.add(lossFunction);
		evaluateLoss(predictorTrainer, lossFunctions);
	}
}
