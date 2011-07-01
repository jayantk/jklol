package com.jayantkrish.jklol.models.loglinear;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.jayantkrish.jklol.models.FactorGraph;

public class LogLinearModel extends FactorGraph {

	private List<DiscreteLogLinearFactor> logLinearFactors;
	private LogLinearParameters features;

	public LogLinearModel(FactorGraph factorGraph, 
			List<DiscreteLogLinearFactor> logLinearFactors, LogLinearParameters features) {
		super(factorGraph);
		this.logLinearFactors = new ArrayList<DiscreteLogLinearFactor>(logLinearFactors);
		this.features = features;
	}

	public List<DiscreteLogLinearFactor> getLogLinearFactors() {
		return Collections.unmodifiableList(logLinearFactors);
	}

	public LogLinearParameters getFeatureSet() {
		return features;
	}
}
