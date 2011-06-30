package com.jayantkrish.jklol.models.bayesnet;

import java.util.Iterator;
import java.util.Map;

import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.util.AllAssignmentIterator;
import com.jayantkrish.jklol.util.Assignment;


/**
 * A CptTableFactor is the typical factor you'd expect in a Bayesian Network. 
 * Its unnormalized probabilities are simply child variable probabilities conditioned on a parent.
 */
public class CptTableFactor extends DiscreteFactor implements CptFactor {

	private VariableNumMap parentVars;
	private VariableNumMap childVars;

	private Cpt cpt;
	private Map<Integer, Integer> cptVarNumMap;

	/**
	 * childrenNums are the variable numbers of the "child" nodes. The CptFactor defines a
	 * probability distribution P(children | parents) over *sets* of child variables. (In the Bayes
	 * Net DAG, there is an edge from every parent to every child, and internally the children are a
	 * directed clique.)
	 *
	 * The factor's CPT comes uninitialized.
	 */
	public CptTableFactor(VariableNumMap parentVars, VariableNumMap childVars) {
		super(parentVars.union(childVars));

		this.parentVars = parentVars;
		this.childVars = childVars;

		cpt = null;
		cptVarNumMap = null;
	}

	/////////////////////////////////////////////////////////////
	// Required methods for Factor 
	/////////////////////////////////////////////////////////////

	public Iterator<Assignment> outcomeIterator() {
		// TODO: mapper iterator for sparse outcomes
		return new AllAssignmentIterator(getVars());
	}

	public double getUnnormalizedProbability(Assignment assignment) {
		return cpt.getProbability(assignment.mappedAssignment(cptVarNumMap));
	}

	//////////////////////////////////////////////////////////////////
	// CPT Factor methods
	/////////////////////////////////////////////////////////////////

	public void clearCpt() {
		this.cpt.clearOutcomeCounts();
	}

	public void addUniformSmoothing(double virtualCounts) {
		cpt.addUniformSmoothing(virtualCounts);
	}

	public void incrementOutcomeCount(Assignment a, double count) {
		cpt.incrementOutcomeCount(a.mappedAssignment(cptVarNumMap), count);
	}

	public void incrementOutcomeCount(Factor marginal, double count) {
		Iterator<Assignment> assignmentIter = outcomeIterator();
		while (assignmentIter.hasNext()) {
			Assignment a = assignmentIter.next();
			incrementOutcomeCount(a, count * marginal.getUnnormalizedProbability(a) / marginal.getPartitionFunction());
		}
	}

	/////////////////////////////////////////////////////////////////////
	// CPTTableFactor methods
	/////////////////////////////////////////////////////////////////////

	/**
	 * Set the CPT associated with this factor to the given CPT. 
	 * cptVarNumMap defines which variable number (of this factor) maps to each 
	 * variable number of the CPT.
	 */
	public void setCpt(Cpt cpt, Map<Integer, Integer> cptVarNumMap) {
		this.cpt = cpt;
		this.cptVarNumMap = cptVarNumMap;
	}

	/**
	 * Get the CPT associated with this factor.
	 */ 
	public Cpt getCpt() {
		return cpt;
	}

	/**
	 * Get an iterator over all possible assignments to the parent variables
	 */
	public Iterator<Assignment> parentAssignmentIterator() {
		return new AllAssignmentIterator(parentVars);
	}

	/**
	 * Get an iterator over all possible assignments to the child variables
	 */
	public Iterator<Assignment> childAssignmentIterator() {
		return new AllAssignmentIterator(childVars);
	}

	public String toString() {
		return cpt.toString();
	}
}