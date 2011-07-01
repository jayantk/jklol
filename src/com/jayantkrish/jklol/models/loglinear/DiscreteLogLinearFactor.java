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
 * A {@link LogLinearFactor} over {@link DiscreteVariable}s.
 */ 
public class DiscreteLogLinearFactor extends DiscreteFactor implements LogLinearFactor {

	private LogLinearParameters featureSet;
	private Set<FeatureFunction> myFeatures;
	private SparseOutcomeTable<Set<FeatureFunction>> sparseFeatures;

	public DiscreteLogLinearFactor(VariableNumMap vars, LogLinearParameters featureSet) {
		super(vars);
		this.featureSet = featureSet;
		myFeatures = new HashSet<FeatureFunction>();
		this.sparseFeatures = new SparseOutcomeTable<Set<FeatureFunction>>(vars.getVariableNums());
	}

	/////////////////////////////////////////////////////////////
	// Required methods for DiscreteFactor 
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

	/* (non-Javadoc)
	 * @see com.jayantkrish.jklol.models.loglinear.LogLinearFactor#addFeature(com.jayantkrish.jklol.models.loglinear.FeatureFunction)
	 */
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

	/* (non-Javadoc)
	 * @see com.jayantkrish.jklol.models.loglinear.LogLinearFactor#getFeatures()
	 */
	public Set<FeatureFunction> getFeatures() {
		return Collections.unmodifiableSet(myFeatures);
	}
}
