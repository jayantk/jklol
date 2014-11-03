package com.jayantkrish.jklol.evaluation;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.Doubles;

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
	public Prediction<I, O> getBestPredictions(I input, O output, int numPredictions) {
	  List<O> predictions = Lists.newArrayList();
	  List<Double> scores = Lists.newArrayList(); 
	  O currentKey = outputProbabilities.lastKey();
	  for (int i = 0; i < numPredictions && currentKey != null; i++) {
	    predictions.add(currentKey);
	    scores.add(Math.log(outputProbabilities.get(currentKey)));
	    currentKey = outputProbabilities.lowerKey(currentKey);
	  }
	  
	  double outputScore = Double.NEGATIVE_INFINITY;
	  if (output != null && baseMap.containsKey(output)) {
	    outputScore = Math.log(outputProbabilities.get(output));
	  }
	  return Prediction.create(input, output, outputScore, Doubles.toArray(scores), predictions);
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