package edu.cmu.ml.rtw.dirichlet;

import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.io.Serializable;

/**
 * Simple wrapper class to store labeled vectors.
 */
public class LabeledVector<T> implements Serializable {

    private String label;
    private Map<T, Double> counts;

    public LabeledVector(String label, Map<T, Double> counts) {
	this.label = label;
	this.counts = counts;
    }

    public static LabeledVector fromIntCounts(String label, Map<String, Integer> counts) {
	Map<String, Double> newCounts = new HashMap<String, Double>();
	for (String key : counts.keySet()) {
	    newCounts.put(key, (double) counts.get(key));
	}
	return new LabeledVector<String>(label, newCounts);
    }

    public String getLabel() {
	return label;
    }

    public Set<T> getContexts() {
	return counts.keySet();
    }

    public Double getCount(T context) {
	return counts.get(context);
    }

    public String toString() {
	StringBuilder b = new StringBuilder();
	b.append(label);
	b.append(" -> ");
	b.append(counts.toString());
	return b.toString();
    }
}