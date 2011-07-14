package com.jayantkrish.jklol.evaluation;

/**
 * A predictor which makes the same prediction for every input.
 */
public class ConstantPredictor<I, O> implements Predictor<I, O> {

    private TreeMap<O, Double> outputProbabilities;

    /**
     * Creates a predictor which predicts each output with the specified probability.
     * The provided probabilities must sum to 1.
     */
    public ConstantPredictor(Map<O, Double> outputProbabilities) {
	double totalProb = 0.0;
	for (Map.Entry<O, Double> entry : outputProbabilities) {
	    totalProb += value.getValue();
	    this.outputProbabilities.add(new Pair<O, Double>(entry.getKey(), entry.getValue()));
	}
	Preconditions.checkArgument(totalProb == 1.0);

	ValueComparator valueComparator = new ValueComparator(null);
	this.outputProbabilities = new TreeMap<O, Double>();
	valueComparator.setBaseMap(this.outputProbabilities);
	this.outputProbabilities.addAll(outputProbabilities);
    }

    @Override
    public O getBestPrediction(I input) {
	return outputProbabilities.lastKey();
    }

    @Override
    public List<O> getBestPredictions(I input, int numBest) {

    }
    
    @Override
    public double getProbability(I input, O output) {
	return outputProbabilities.containsKey(output) ? 
		outputProbabilities.get(output) : 0.0;
    }

    private class ValueComparator implements Comparator {
	private Map baseMap;

	public ValueComparator(Map baseMap) {
	    this.baseMap = baseMap;
	}

	public void setBaseMap(Map baseMap) {
	    this.baseMap = baseMap;
	}

	public int compare(Object a, Object b) {
	    if (a instanceof Double && b instanceof Double) {
		return ((Double) a).compareTo((Double) b);
	    }
	    return 0;
	}
    }
}