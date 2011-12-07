package com.jayantkrish.jklol.evaluation;

import java.util.List;

/**
 * An Evaluation is a technique for computing the loss of a predictor.
 *
 * @param I the type of the inputVar data that predictions are based on.
 * @param O the type of the outputVar prediction.
 */
public interface Evaluation<I, O> {

    /**
     * Evaluate a particular learning algorithm on a set of loss functions.
     * The losses are accumulated within the lossFunctions and can be retrieved
     * by examining them.
     */
    public void evaluateLoss(PredictorTrainer<I, O> predictorTrainer, 
    		List<LossFunction<I, O>> lossFunctions);
    
    /**
     * {@see evaluateLoss}
     */
    public void evaluateLoss(PredictorTrainer<I, O> predictorTrainer, 
    		LossFunction<I, O> ... lossFunctions);
    
    /**
     * {@see evaluateLoss}
     */
    public void evaluateLoss(PredictorTrainer<I, O> predictorTrainer, 
    		LossFunction<I, O> lossFunction);
}
