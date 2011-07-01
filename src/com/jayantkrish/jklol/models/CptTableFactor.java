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
    private Map<Integer, Integer> varToCptVarNumMap;
    private Map<Integer, Integer> cptVarToVarNumMap;

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
	varToCptVarNumMap = null;
	cptVarToVarNumMap = null;
    }

    /////////////////////////////////////////////////////////////
    // Required methods for Factor 
    /////////////////////////////////////////////////////////////

    public Iterator<Assignment> outcomeIterator() {
	return new MappingAssignmentIterator(cpt.assignmentIterator(), cptVarToVarNumMap);
    }

    public double getUnnormalizedProbability(Assignment assignment) {
	return cpt.getProbability(assignment.mappedAssignment(varToCptVarNumMap));
    }

    public Set<Assignment> getAssignmentsWithEntry(int varNum, Set<Integer> varValues) {
	int mappedInd = varToCptVarNumMap.get(varNum);
	Set<Assignment> cptAssignments = cpt.getAssignmentsWithEntry(mappedInd, varValues);
	Set<Assignment> myAssignments = new HashSet<Assignment>();
	for (Assignment a : cptAssignments) {
	    myAssignments.add(a.mappedAssignment(cptVarToVarNumMap));
	}
	return myAssignments;
    }

    //////////////////////////////////////////////////////////////////
    // CPT Factor methods
    /////////////////////////////////////////////////////////////////


    public void clearCpt() {
	this.cpt.clearOutcomeCounts();
    }

    public void addUniformSmoothing(double virtualCounts) {
	Iterator<Assignment> assignmentIter = cpt.assignmentIterator();
	while (assignmentIter.hasNext()) {
	    cpt.setOutcomeCount(assignmentIter.next(), virtualCounts);
	}
    }

    public void incrementOutcomeCount(Assignment a, double count) {
	cpt.incrementOutcomeCount(a.mappedAssignment(varToCptVarNumMap), count);
    }

    public void incrementOutcomeCount(Factor marginal, double count) {
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
     * varToCptVarNumMap defines which variable number (of this factor) maps to each 
     * variable number of the CPT.
     */
    public void setCpt(Cpt cpt, Map<Integer, Integer> varToCptVarNumMap) {
	this.cpt = cpt;
	this.varToCptVarNumMap = varToCptVarNumMap;
	this.cptVarToVarNumMap = new HashMap<Integer, Integer>();
	for (Integer i : varToCptVarNumMap.keySet()) {
	    cptVarToVarNumMap.put(varToCptVarNumMap.get(i), i);
	}
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


    public class MappingAssignmentIterator implements Iterator<Assignment> {
	
	private Iterator<Assignment> baseIter;
	private Map<Integer, Integer> varNumMap;

	public MappingAssignmentIterator(Iterator<Assignment> baseIter,
		Map<Integer, Integer> varNumMap) {
	    this.baseIter = baseIter;
	    this.varNumMap = varNumMap;
	}

	public boolean hasNext() {
	    return baseIter.hasNext();
	}

	public Assignment next() {
	    Assignment a = baseIter.next();
	    return a.mappedAssignment(varNumMap);
	}

	public void remove() {
	    throw new UnsupportedOperationException();
	}
    }
}

