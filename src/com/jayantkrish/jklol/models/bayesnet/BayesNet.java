package com.jayantkrish.jklol.models.bayesnet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.jayantkrish.jklol.models.FactorGraph;

public class BayesNet extends FactorGraph {

	private List<CptFactor<?>> cptFactors;

	public BayesNet(FactorGraph factorGraph, List<CptFactor<?>> cptFactors) {
		super(factorGraph);
		this.cptFactors = new ArrayList<CptFactor<?>>(cptFactors);
	}

	public List<CptFactor<?>> getCptFactors() {
		return Collections.unmodifiableList(cptFactors);
	}	
}
