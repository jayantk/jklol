package com.jayantkrish.jklol.evaluation;

import com.jayantkrish.jklol.util.DefaultHashMap;
import com.jayantkrish.jklol.util.Pair;

/**
 * Common baseline prediction methods.
 * 
 * @author jayant
 */
public class Baselines {
	
	/**
	 * Trains a predictor which predicts the most frequent label in the training data,
	 * and estimates label probabilities independently of the input data point.    
	 * @author jayant
	 *
	 * @param <I>
	 * @param <O>
	 */
	public static class MostFrequentLabel<I, O> implements PredictorTrainer<I, O> {

		@Override
		public Predictor<I, O> train(Iterable<Pair<I, O>> trainingData) {
			DefaultHashMap<O, Double> outputCounts = new DefaultHashMap<O, Double>(0.0);
			double total = 0.0;
			for (Pair<I, O> datum : trainingData) {
				outputCounts.put(datum.getRight(), outputCounts.get(datum.getRight()) + 1.0);
				total++;
			}
			
			for (O key : outputCounts.keySet()) {
				outputCounts.put(key, outputCounts.get(key) / total);
			}
			return new ConstantPredictor<I, O>(outputCounts.getBaseMap());
		}
	}
}
