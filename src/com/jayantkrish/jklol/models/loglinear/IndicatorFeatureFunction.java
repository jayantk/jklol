package com.jayantkrish.jklol.models.loglinear;

import java.util.*;

import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.util.Assignment;

/**
 * An IndicatorFeatureFunction is 1 if the outcome is in a certain set of values, 0 otherwise.
 */
public class IndicatorFeatureFunction implements FeatureFunction {

	private Set<Assignment> assignments;

	/**
	 * Convenience method for the feature which is only 1 for
	 * the specified assignment.
	 */
	public IndicatorFeatureFunction(Assignment assignment) {
		assignments = new HashSet<Assignment>();
		assignments.add(assignment);
	}

	public IndicatorFeatureFunction(Set<Assignment> assignments) {
		this.assignments = new HashSet<Assignment>(assignments);
	}

	@Override
	public double getValue(Assignment other) {
		if (assignments.contains(other)) {
			return 1.0;
		}
		return 0.0;
	}

	@Override
	public Iterator<Assignment> getNonzeroAssignments() {
		return assignments.iterator();
	}

	@Override
	public List<Integer> getVarNums() {
		return assignments.iterator().next().getVariableNums();
	}

	@Override
	public double computeExpectation(Factor factor, Assignment assignment) {
	  double expectedValue = 0.0;
	  
	  Collection<Integer> inputAssignmentVars = assignment.getVariableNums();
	  for (Assignment a : assignments) {
	    if (a.intersection(inputAssignmentVars).equals(assignment)) {
	      expectedValue += factor.getUnnormalizedProbability(a.removeAll(inputAssignmentVars));
	    }
	  }
	  return expectedValue;
	}

	@Override
	public String toString() {
		return "Ind(" + assignments.toString() + ")";
	}
}