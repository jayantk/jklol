package edu.cmu.ml.rtw.dirichlet;

import java.util.Map;
import java.util.List;
import java.util.HashMap;

/**
 * A multinomial distribution over a set of items of type T.
 */
public class Multinomial<T> implements Distribution<T> {

    private List<T> items;
    private Map<T, Integer> itemIndex;
    private double[] probs;

    public Multinomial(List<T> items, double[] probs) {
	assert(items.size() == probs.length);
	this.items = items;
	this.probs = probs;
	itemIndex = new HashMap<T, Integer>();
	for (int i = 0; i < items.size(); i++) {
	    itemIndex.put(items.get(i), i);
	}
    }

    public T sample() {
	int drawnIndex = -1;
	double theDraw = Math.random();
	double curSum = 0.0;
	for (int i = 0; i < probs.length; i++) {
	    curSum += probs[i];
	    if (theDraw <= curSum) {
		drawnIndex = i;
		break;
	    }
	}
	return items.get(drawnIndex);
    }

    public double getDensity(T point) {
	return probs[itemIndex.get(point)];
    }

    public double getDensity(LabeledVector<T> point) {
	double density = 1.0;
	for (T context : point.getContexts()) {
	    double count = point.getCount(context);
	    density *= Math.pow(getDensity(context), count);
	}
	return density;
    }

    public String toString() {
	StringBuilder b = new StringBuilder();
	b.append("[");
	for (int i = 0; i < items.size(); i++) {
	    b.append(items.get(i).toString() + ":" + probs[i] + ",");
	}
	b.append("]");
	return b.toString();
    }
}

