package com.jayantkrish.jklol.models;

import java.util.*;
import java.lang.StringBuilder;


/**
 * A CptTableFactor is a factor in a Bayes Net, parameterized by a single conditional probability table.
 */
public class CptTableFactor extends CptFactor {

    private List<Integer> children;
    private List<Variable> childVars;
    private List<Integer> parents;
    private List<Variable> parentVars;

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
    public CptTableFactor(List<Integer> allVarNums, List<Variable> allVars, 
	    List<Integer> parentNums, List<Variable> parentVars, 
	    List<Integer> childrenNums, List<Variable> childrenVars) {
	super(allVarNums, allVars);

	this.parents = parentNums;
	this.parentVars = parentVars;
	this.children = childrenNums;
	this.childVars = childrenVars;

	cpt = null;
	cptVarNumMap = null;
    }

    /////////////////////////////////////////////////////////////
    // Required methods for Factor 
    /////////////////////////////////////////////////////////////

    public Iterator<Assignment> outcomeIterator() {
	// TODO: mapper iterator for sparse outcomes
	return new AllAssignmentIterator(getVarNums(), getVars());
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

    public void incrementOutcomeCount(DiscreteFactor marginal, double count) {
	Iterator<Assignment> assignmentIter = marginal.outcomeIterator();
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
	return new AllAssignmentIterator(parents, parentVars);
    }

    /**
     * Get an iterator over all possible assignments to the child variables
     */
    public Iterator<Assignment> childAssignmentIterator() {
	return new AllAssignmentIterator(children, childVars);
    }

    public String toString() {
	return cpt.toString();
    }
}

