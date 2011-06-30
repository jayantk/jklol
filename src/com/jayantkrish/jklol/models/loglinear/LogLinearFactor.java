package com.jayantkrish.jklol.models.loglinear;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.util.AllAssignmentIterator;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.SparseOutcomeTable;


/**
 * A LogLinearFactor represents a probability distribution as the exponential of a weighted sum of
 * feature functions.
 */ 
public class LogLinearFactor extends DiscreteFactor {

	private FeatureSet featureSet;
	private Set<FeatureFunction> myFeatures;
	private SparseOutcomeTable<Set<FeatureFunction>> sparseFeatures;

	public LogLinearFactor(VariableNumMap vars, FeatureSet featureSet) {
		super(vars);
		this.featureSet = featureSet;
		myFeatures = new HashSet<FeatureFunction>();
		this.sparseFeatures = new SparseOutcomeTable<Set<FeatureFunction>>(vars.getVariableNums());
	}

	/////////////////////////////////////////////////////////////
	// Required methods for Factor 
	/////////////////////////////////////////////////////////////

	public Iterator<Assignment> outcomeIterator() {
		return new AllAssignmentIterator(getVars());
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
}
