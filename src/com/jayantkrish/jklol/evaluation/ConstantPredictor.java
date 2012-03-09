package com.jayantkrish.jklol.evaluation;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * A predictor which makes the same prediction for every inputVar.
 */
public class ConstantPredictor<I, O> extends AbstractPredictor<I, O> {

  private Map<O, Double> baseMap;
	private TreeMap<O, Double> outputProbabilities;

	/**
	 * Creates a predictor which predicts each outputVar with the specified probability.
	 * The provided probabilities must sum to 1.
	 */
	public ConstantPredictor(Map<O, Double> outputProbabilities) {
		double totalProb = 0.0;
		for (Map.Entry<O, Double> entry : outputProbabilities.entrySet()) {
			totalProb += entry.getValue();
		}
		// Run the check but ignore possible numerical precision errors.
		Preconditions.checkArgument(totalProb > 0.999 && totalProb < 1.001);
		baseMap = Maps.newHashMap(outputProbabilities);
		ValueComparator<Double> valueComparator = new ValueComparator<Double>(baseMap);
		this.outputProbabilities = new TreeMap<O, Double>(valueComparator);
		this.outputProbabilities.putAll(outputProbabilities);
	}

	@Override
	public O getBestPrediction(I input) {
		return outputProbabilities.lastKey();
	}

	@Override
	public List<O> getBestPredictions(I input, int numBest) {
		List<O> predictions = Lists.newArrayList();
		O currentKey = outputProbabilities.lastKey();
		for (int i = 0; i < numBest && currentKey != null; i++) {
			predictions.add(currentKey);
			currentKey = outputProbabilities.lowerKey(currentKey);
		}
		return predictions;
	}

	@Override
	public double getProbability(I input, O output) {
		return baseMap.containsKey(output) ? 
				outputProbabilities.get(output) : 0.0;
	}
	
	@Override
	public String toString() {
		return "Constant Predictor: " + outputProbabilities;
	}

	private class ValueComparator<T extends Comparable<T>> implements Comparator<O> {
		private Map<O, T> baseMap;

		public ValueComparator(Map<O, T> baseMap) {
			this.baseMap = baseMap;
		}

		public int compare(O a, O b) {
			return baseMap.get(a).compareTo(baseMap.get(b));
		}
	}
}