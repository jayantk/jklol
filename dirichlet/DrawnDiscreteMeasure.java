package edu.cmu.ml.rtw.dirichlet;

import java.util.List;

public class DrawnDiscreteMeasure<T> implements Distribution<T> {
    
    private List<Double> binCounts;
    private List<T> baseSamples;
    private double newBinCount;
    private double denom;
    private Distribution<T> baseDist;
    
    public DrawnDiscreteMeasure(List<Double> binCounts, List<T> baseSamples, 
	    double concentration, Distribution<T> baseDist) {
	this.binCounts = binCounts;
	this.baseSamples = baseSamples;
	this.newBinCount = concentration;
	this.baseDist = baseDist;
	
	this.denom = newBinCount;
	for (Double d : binCounts) {
	    denom += d;
	}
    }
    
    public T sample() {
	double draw = denom * Math.random();
	
	double sum = 0;
	int i = 0;
	for (i = 0; i < binCounts.size(); i++) {
	    sum += binCounts.get(i);
	    if (sum >= draw) {
		break;
	    }
	}
	
	if (i < binCounts.size()) {
	    // We sampled again from a known bin.
	    return baseSamples.get(i);
	} else {
	    // New bin.
	    return baseDist.sample();
	}
    }
    
    public double getDensity(T point) {
	throw new RuntimeException();
    }
}
