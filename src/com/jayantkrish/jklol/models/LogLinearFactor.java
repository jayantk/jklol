package com.jayantkrish.jklol.models;

import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Collection;
import java.util.Collections;
import java.lang.UnsupportedOperationException;


/**
 * A LogLinearFactor represents a probability distribution as the exponential of a weighted sum of
 * feature functions.
 */ 
public class LogLinearFactor extends DiscreteFactor {

    private FeatureSet featureSet;
    private Set<FeatureFunction> myFeatures;
    private SparseOutcomeTable<Set<FeatureFunction>> sparseFeatures;

    public LogLinearFactor(List<Integer> varNums, List<Variable> variables, FeatureSet featureSet) {
	super(varNums, variables);
	this.featureSet = featureSet;
	myFeatures = new HashSet<FeatureFunction>();
	this.sparseFeatures = new SparseOutcomeTable<Set<FeatureFunction>>(varNums);
    }

    /////////////////////////////////////////////////////////////
    // Required methods for Factor 
    /////////////////////////////////////////////////////////////

    public Iterator<Assignment> outcomeIterator() {
	return new AllAssignmentIterator(getVarNums(), getVars());
    }

    public double getUnnormalizedProbability(Assignment assignment) {
	double weight = 0.0;
	if (sparseFeatures.containsKey(assignment)) {
	    for (FeatureFunction f : sparseFeatures.get(assignment)) {
		weight += featureSet.getFeatureWeight(f) * f.getValue(assignment);
	    }
	}
	return Math.exp(weight);
    }

    //////////////////////////////////////////////////////////////
    // Feature manipulation / update methods
    //////////////////////////////////////////////////////////////

    public void addFeature(FeatureFunction feature) {
	featureSet.addFeature(feature);
	myFeatures.add(feature);
	
	Iterator<Assignment> assignmentIter = feature.getNonzeroAssignments();
	while (assignmentIter.hasNext()) {
	    Assignment a = assignmentIter.next();
	    if (!sparseFeatures.containsKey(a)) {
		sparseFeatures.put(a, new HashSet<FeatureFunction>());
	    }
	    sparseFeatures.get(a).add(feature);
	}
    }

    public Set<FeatureFunction> getFeatures() {
	return Collections.unmodifiableSet(myFeatures);
    }

    private int countAssignments(FeatureFunction f) {
	Iterator<Assignment> assignmentIter = f.getNonzeroAssignments();
	int count = 0;
	while (assignmentIter.hasNext()) {
	    count++;
	}
	return count;
    }



}