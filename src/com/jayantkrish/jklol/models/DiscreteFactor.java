package com.jayantkrish.jklol.models;

import com.jayantkrish.jklol.util.Pair;
import com.jayantkrish.jklol.util.PairComparator;

import java.util.*;

/**
 * A Factor in a Markov Network.
 */
public abstract class DiscreteFactor {

    protected List<Integer> varNums;
    protected List<Variable> vars;

    protected double partitionFunction;

    public DiscreteFactor(List<Integer> varNums, List<Variable> variables) {
	SortedMap<Integer, Variable> sortedVars = new TreeMap<Integer, Variable>();
	for (int i = 0; i < varNums.size(); i++) {
	    sortedVars.put(varNums.get(i), variables.get(i));
	}

	this.varNums = new ArrayList<Integer>(sortedVars.keySet());
	this.vars = new ArrayList<Variable>();
	for (int i = 0; i < this.varNums.size(); i++) {
	    this.vars.add(sortedVars.get(this.varNums.get(i)));
	}

	this.partitionFunction = -1.0;
    }

    /**
     * Get an iterator over all outcomes with nonzero probability. Each Assignment in the iterator
     * represents a single outcome.
     */
    public abstract Iterator<Assignment> outcomeIterator();
   
    /**
     * Compute the unnormalized probability of an assignment.
     */ 
    public abstract double getUnnormalizedProbability(Assignment assignment);


    /**
     * Get all outcomes with nonzero probability.
     */
    public List<Assignment> outcomes() {
	List<Assignment> allOutcomes = new ArrayList<Assignment>();
	Iterator<Assignment> iter = outcomeIterator();
	while (iter.hasNext()) {
	    allOutcomes.add(new Assignment(iter.next()));
	}
	return allOutcomes;
    }
    /**
     * Get the variable numbers of all variables that participate in this factor. Variables will
     * always be returned in ascending order by variable number.
     */
    public List<Integer> getVarNums() {
	return Collections.unmodifiableList(varNums);
    }

    /**
     * Get the base variables which define the possible values for each index. The list of variables
     * is the same length as the list of variable numbers and each index is a corresponding pair.
     */
    public List<Variable> getVars() {
	return Collections.unmodifiableList(vars);
    }

    /**
     * Get the assignment corresponding to a particular setting of the variables in this factor.
     */
    public Assignment outcomeToAssignment(List<? extends Object> outcome) {
	assert outcome.size() == varNums.size();

	List<Integer> outcomeValueInds = new ArrayList<Integer>(outcome.size());
	for (int i = 0; i < vars.size(); i++) {
	    outcomeValueInds.add(vars.get(i).getValueIndexObject(outcome.get(i)));
	}
	return new Assignment(varNums, outcomeValueInds);
    }

    /**
     * Get the assignment corresponding to a particular setting of the variables in this factor.
     */
    public Assignment outcomeToAssignment(Object[] outcome) {
	return outcomeToAssignment(Arrays.asList(outcome));
    }

    /**
     * Compute the unnormalized probability of an outcome.
     */
    public double getUnnormalizedProbability(List<? extends Object> outcome) {
	Assignment a = outcomeToAssignment(outcome);
	return getUnnormalizedProbability(a);
    }

    /**
     * Compute the unnormalized probability of an outcome, using the given
     * variables in the given order.
     */
    public double getUnnormalizedProbability(List<Integer> varNumOrder, List<? extends Object> outcome) {
	List<Integer> usedVarNums = new ArrayList<Integer>(outcome.size());
	List<Integer> outcomeValueInds = new ArrayList<Integer>(outcome.size());
	for (int j = 0; j < varNumOrder.size(); j++) {
	    for (int i = 0; i < vars.size(); i++) {
		if (varNums.get(i) == varNumOrder.get(j)) {
		    usedVarNums.add(varNums.get(i));
		    outcomeValueInds.add(vars.get(i).getValueIndexObject(outcome.get(j)));
		}
	    }
	}

	assert usedVarNums.size() == varNums.size();

	Assignment a = new Assignment(usedVarNums, outcomeValueInds);
	return getUnnormalizedProbability(a);
    }

    /**
     * Get all assignments to this variable with nonzero probability that contain the specified
     * variable number -> (any value in varValues) mapping.
     *
     * The default implementation of this method is fairly inefficient -- overriding it 
     * with a more efficient implementation should significantly improve performance.
     */
    public Set<Assignment> getAssignmentsWithEntry(int varNum, Set<Integer> varValues) {
	Set<Assignment> assignments = new HashSet<Assignment>();
	Iterator<Assignment> iter = outcomeIterator();
	while (iter.hasNext()) {
	    Assignment a = iter.next();
	    if (varValues.contains(a.getVarValue(varNum))) {
		assignments.add(new Assignment(a));
	    }
	}
	return assignments;
    }


    ///////////////////////////////////////////////////////////////////////////////////
    // Methods for performing inference.
    ///////////////////////////////////////////////////////////////////////////////////


    /**
     * Multiplies together the inbound message factors, then marginalizes out all variables not in
     * "variablesToRetain".
     *
     * In most cases, this default method should be sufficient. Factors may choose to override this
     * in order to provide specialized functionality.
     */ 
    public DiscreteFactor sumProduct(List<DiscreteFactor> inboundMessages, Collection<Integer> variablesToRetain) {
	List<DiscreteFactor> allFactors = new ArrayList<DiscreteFactor>(inboundMessages);
	allFactors.add(this);
	return TableFactor.sumProductTableFactor(allFactors, variablesToRetain);
    }

    /**
     * Multiplies together the inbound message factors, then maximizes out all variables not in
     * "variablesToRetain".
     *
     * In most cases, this default method should be sufficient. Factors may choose to override this
     * in order to provide specialized functionality.
     */ 
    public DiscreteFactor maxProduct(List<DiscreteFactor> inboundMessages, Collection<Integer> variablesToRetain) {
	List<DiscreteFactor> allFactors = new ArrayList<DiscreteFactor>(inboundMessages);
	allFactors.add(this);
	return TableFactor.maxProductTableFactor(allFactors, variablesToRetain);
    }

    /**
     * Returns the result of multiplying the provided factors with this one.
     */
    public DiscreteFactor product(List<DiscreteFactor> factors) {
	List<DiscreteFactor> allFactors = new ArrayList<DiscreteFactor>(factors);
	allFactors.add(this);
	return TableFactor.productFactor(allFactors);
    }

    /**
     * Convenience wrapper for multiplying two factors.
     */
    public DiscreteFactor product(DiscreteFactor f) {
	List<DiscreteFactor> allFactors = new ArrayList<DiscreteFactor>(1);
	allFactors.add(f);
	return this.product(allFactors);
    }

    /**
     * Get a new factor which conditions on the observed variables in the
     * assignment.
     *
     * The returned factor still contains the same variables as the original, but has appropriate
     * portions of the factor distribution zeroed out.
     */
    public DiscreteFactor conditional(Assignment a) {
	
	Set<Integer> intersection = new HashSet<Integer>(varNums);
	intersection.retainAll(a.getVarNumsSorted());

	// If the assignment doesn't share any variables with this factor, then this factor is unaffected.
	if (intersection.size() == 0) {
	    return this;
	}

	TableFactor returnFactor = new TableFactor(varNums, vars);
	Assignment subAssignment = a.subAssignment(new ArrayList<Integer>(intersection));
	// Another easy case that's not handled by the later code.
	if (intersection.size() == varNums.size()) {
	    returnFactor.setWeight(subAssignment, 
		    getUnnormalizedProbability(subAssignment));
	    return returnFactor;
	}

	// Some variables are conditioned on, and some are not.
	List<Integer> remainingNums = new ArrayList<Integer>();
	List<Variable> remainingVars = new ArrayList<Variable>();
	for (int i = 0; i < varNums.size(); i++) {
	    if (!intersection.contains(varNums.get(i))) {
		remainingNums.add(varNums.get(i));
		remainingVars.add(vars.get(i));
	    }
	}

	Set<Assignment> possibleAssignments = null;
	for (Integer varNum : intersection) {
	    if (possibleAssignments == null) {
		possibleAssignments = getAssignmentsWithEntry(varNum, Collections.singleton(a.getVarValue(varNum)));
	    } else {
		possibleAssignments.retainAll(getAssignmentsWithEntry(varNum, Collections.singleton(a.getVarValue(varNum))));
	    }
	}

	// new AllAssignmentIterator(remainingNums, remainingVars); 
	Iterator<Assignment> iter = possibleAssignments.iterator();
	while (iter.hasNext()) {
	    Assignment partialAssignment = iter.next().subAssignment(remainingNums);
	    Assignment full = partialAssignment.jointAssignment(subAssignment);
	    returnFactor.setWeight(full, getUnnormalizedProbability(full));
	}
	return returnFactor;
    }

    /**
     * Return a factor with the specified variable marginalized out (by summing)
     */ 
    public DiscreteFactor marginalize(Integer varNum) {
	return marginalize(Arrays.asList(new Integer[] {varNum}));
    }

    /**
     * Return a factor with the specified variables marginalized out.
     */ 
    public DiscreteFactor marginalize(Collection<Integer> varNumsToEliminate) {
	return marginalize(varNumsToEliminate, true);
    }

    /**
     * Return a factor with the specified variable marginalized out (by maximizing)
     */ 
    public DiscreteFactor maxMarginalize(Integer varNum) {
	return maxMarginalize(Arrays.asList(new Integer[] {varNum}));
    }

    /**
     * Return a factor with the specified variables marginalized out (by maximizing)
     */ 
    public DiscreteFactor maxMarginalize(Collection<Integer> varNumsToEliminate) {
	return marginalize(varNumsToEliminate, false);
    }

    /*
     * Sums or maximizes out a particular set of variables. If useSum is true,
     * probabilities are summed.
     */
    protected DiscreteFactor marginalize(Collection<Integer> varNumsToEliminate, boolean useSum) {
	Set<Integer> varNumsToEliminateSet = new HashSet<Integer>(varNumsToEliminate);

	List<Variable> varsToRetain = new ArrayList<Variable>();
	List<Integer> varNumsToRetain = new ArrayList<Integer>();
	List<Integer> varNumIndicesToRetain = new ArrayList<Integer>();
	for (int i = 0; i < vars.size(); i++) {
	    if (!varNumsToEliminateSet.contains(varNums.get(i))) {
		varNumIndicesToRetain.add(i);
		varsToRetain.add(vars.get(i));
		varNumsToRetain.add(varNums.get(i));
	    }
	}
	
	TableFactor returnFactor = new TableFactor(varNumsToRetain, varsToRetain);

	Iterator<Assignment> outcomeIterator = outcomeIterator();
	while (outcomeIterator.hasNext()) {
	    Assignment assignment = outcomeIterator.next();
	    Assignment subAssignment = assignment.subAssignment(varNumsToRetain);

	    double val = 0.0;
	    if (useSum) {
		val = returnFactor.getUnnormalizedProbability(subAssignment) + getUnnormalizedProbability(assignment);
	    } else {
		val = Math.max(returnFactor.getUnnormalizedProbability(subAssignment), 
			getUnnormalizedProbability(assignment));
	    }
	    returnFactor.setWeight(subAssignment, val);
	}
	return returnFactor;
    }


    /**
     * Compute the expected value of a feature function (over the same set of variables as this factor) 
     */
    public double computeExpectation(FeatureFunction f) {
	double expectedValue = 0.0;
	double denom = 0.0;

	Iterator<Assignment> outcomeIterator = outcomeIterator();
	while (outcomeIterator.hasNext()) {
	    Assignment assignment = outcomeIterator.next();
	    expectedValue += getUnnormalizedProbability(assignment) * f.getValue(assignment);
	    denom += getUnnormalizedProbability(assignment);
	}
	return expectedValue / denom;
    }
    
    /**
     * Get the partition function = denominator = total sum probability of all assignments.
     */ 
    public double getPartitionFunction() {
	if (partitionFunction != -1.0) {
	    return partitionFunction;
	}

	partitionFunction = 0.0;
	Iterator<Assignment> outcomeIterator = outcomeIterator();
	while (outcomeIterator.hasNext()) {
	    partitionFunction += getUnnormalizedProbability(outcomeIterator.next());
	}
	return partitionFunction;
    }

    /**
     * Sample a random assignment to the variables in this factor according to this factor's
     * probability distribution.
     */
    public Assignment sample() {
	double draw = Math.random();
	double partitionFunction = getPartitionFunction();
	double sumProb = 0.0;
	Iterator<Assignment> iter = outcomeIterator();
	Assignment a = null;
	while (iter.hasNext() && sumProb <= draw) {
	    a = iter.next();
	    sumProb += getUnnormalizedProbability(a) / partitionFunction;
	}
	return a;	
    }

    /**
     * Get most likely assignments.
     */
    public List<Assignment> mostLikelyAssignments(int numAssignments) {
	Iterator<Assignment> iter = outcomeIterator();
	PriorityQueue<Pair<Double, Assignment>> pq = new PriorityQueue(numAssignments, 
		new PairComparator<Double, Assignment>());
	while (iter.hasNext()) {
	    Assignment a = iter.next();
	    pq.offer(new Pair<Double, Assignment>(getUnnormalizedProbability(a), new Assignment(a)));
	    if (pq.size() > numAssignments) {
		pq.poll();
	    }
	}

	List<Assignment> mostLikely = new ArrayList<Assignment>();
	while (pq.size() > 0) {
	    mostLikely.add(pq.poll().getRight());
	}
	Collections.reverse(mostLikely);
	return mostLikely;
    }



}