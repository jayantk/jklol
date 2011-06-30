package com.jayantkrish.jklol.models.loglinear;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.jayantkrish.jklol.models.FactorGraph;

public class LogLinearModel extends FactorGraph {

	private List<LogLinearFactor> logLinearFactors;
	private FeatureSet features;

	public LogLinearModel(FactorGraph factorGraph, 
			List<LogLinearFactor> logLinearFactors, FeatureSet features) {
		super(factorGraph);
		this.logLinearFactors = new ArrayList<LogLinearFactor>(logLinearFactors);
		this.features = features;
	}

	public List<LogLinearFactor> getLogLinearFactors() {
		return Collections.unmodifiableList(logLinearFactors);
	}

	public FeatureSet getFeatureSet() {
		return features;
	}
}
