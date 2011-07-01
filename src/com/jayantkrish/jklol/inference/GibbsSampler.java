package com.jayantkrish.jklol.inference;

import com.jayantkrish.jklol.util.*;
import com.jayantkrish.jklol.models.*;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Implements Gibbs sampling for computing marginals. 
 */
public class GibbsSampler implements InferenceEngine {

    private FactorGraph factorGraph;
    private int burnIn;

    public GibbsSampler(int numBurnInSamples) {
	this.burnIn = numBurnInSamples;
    }

    public void setFactorGraph(FactorGraph f) {
	factorGraph = f;
    }

    public void computeMarginals() {
	computeMarginals(Assignment.EMPTY);
    }

    public void computeMarginals(Assignment assignment) {
	// TODO: fix assignment...

	/*
	List<Variable> vars = factorGraph.getVariables();
	List<Factor> factors = factorGraph.getFactors();
	Assignment curSample = factorGraph.getInitialSample(); // TODO
	Sampler sampler = null;
	Variable var = null;
	for (int i = 0; i < burnIn; i++) {
	    for (int j = 0; j < vars.size(); j++) {
		var = vars.get(j);
		sampler = getSampler(j);
		Set<Integer> factorNums = factorGraph.getFactorsWithVariable(j);
		sampler.sample(j, factors, factorNums);
		
	    }
	}
	*/
    }

    
    /**
     * Retrieves an approximation to the marginal distribution over the provided variables. 
     */
    public Factor getMarginal(List<Integer> varNums) {
	throw new UnsupportedOperationException("not yet implemented");
    }

    /**
     * GibbsSampler cannot compute max marginals.
     */
    public void computeMaxMarginals() {
	throw new UnsupportedOperationException("GibbsSampler cannot compute max marginals");
    }

    /**
     * GibbsSampler cannot compute max marginals.
     */
    public void computeMaxMarginals(Assignment assignment) {
	throw new UnsupportedOperationException("GibbsSampler cannot compute max marginals");
    }
}
