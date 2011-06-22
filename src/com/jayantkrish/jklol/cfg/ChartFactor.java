
package com.jayantkrish.jklol.cfg;

import com.jayantkrish.jklol.models.*;
import java.util.*;


/**
 * A ChartFactor represents conditional distributions over the root variable of 
 * CFG parses.
 */
public class ChartFactor extends DiscreteFactor {

    private ParseChart chart;
    private int parentVarNum;
    private int childVarNum;
    private Variable<Production> parentVar;
    private Variable<List<Production>> childVar;

    private DiscreteFactor parentFactor;
    private Map<List<Production>, Double> childProbs;

    public ChartFactor(ParseChart chart, Variable<Production> parentVar, 
	    Variable<List<Production>> childVar, int parentVarNum, int childVarNum, 
	    Map<List<Production>, Double> childProbs) {
	super(Arrays.asList(new Integer[] {parentVarNum, childVarNum}), 
		Arrays.asList(new Variable[] {parentVar, childVar}));

	assert chart.getInsideCalculated() == true;
	assert chart.getOutsideCalculated() == false;
	assert childProbs.size() == 1;

	this.chart = chart;
	this.parentVarNum = parentVarNum;
	this.parentVar = parentVar;
	this.childVarNum = childVarNum;
	this.childVar = childVar;
	this.childProbs = childProbs;

	// Using a helper table factor makes many methods trivial to implement.
	TableFactor tempFactor = new TableFactor(Arrays.asList(new Integer[] {parentVarNum}), 
		Arrays.asList(new Variable[] {parentVar}));
	Map<Production, Double> rootEntries = chart.getInsideEntries(0, chart.chartSize() - 1);
	List<Production> value = new ArrayList<Production>();
	value.add(null);
	for (Production p : rootEntries.keySet()) {
	    value.set(0, p);
	    tempFactor.setWeightList(value, rootEntries.get(p));
	}
	parentFactor = tempFactor;
    }


    /**
     * If we ever incorporate other factors into this one, use this constructor so we keep track of
     * the other probability distributions.
     */
    public ChartFactor(ParseChart chart, Variable<Production> parentVar, Variable<List<Production>> childVar,
	    int parentVarNum, int childVarNum, Map<List<Production>, Double> childProbs, DiscreteFactor parentFactor) {
	super(Arrays.asList(new Integer[] {parentVarNum, childVarNum}), 
		Arrays.asList(new Variable[] {parentVar, childVar}));

	assert chart.getInsideCalculated() == true;
	assert chart.getOutsideCalculated() == false;
	assert childProbs.size() == 1;

	this.chart = chart;
	this.parentVarNum = parentVarNum;
	this.parentVar = parentVar;
	this.childVarNum = childVarNum;
	this.childVar = childVar;
	this.parentFactor = parentFactor;
	this.childProbs = childProbs;
    }

    public ParseChart getChart() {
	return chart;
    }

    public Map<Production, Double> getRootMarginal() {
	Map<Production, Double> marginal = new HashMap<Production, Double>();
	Iterator<Assignment> iter = parentFactor.outcomeIterator();
	while (iter.hasNext()) {
	    Assignment a = iter.next();
	    marginal.put(parentVar.getValue(a.getVarValue(parentVarNum)), 
		    parentFactor.getUnnormalizedProbability(a));
	}
	return marginal;
    }

    public Map<List<Production>, Double> getTerminalMessage() {
	return childProbs;
    }

    /////////////////////////////////////////////////////////////
    // Required methods for Factor 
    /////////////////////////////////////////////////////////////

    public Iterator<Assignment> outcomeIterator() {
	return new AssignmentWrapperIterator(childVarNum, 
		childVar.getValueIndex(childProbs.keySet().iterator().next()), parentFactor.outcomeIterator());
    }

    public double getUnnormalizedProbability(Assignment a) {
	if (childProbs.containsKey(childVar.getValue(a.getVarValue(childVarNum)))) {
	    return parentFactor.getUnnormalizedProbability(
		    a.subAssignment(Arrays.asList(new Integer[] {parentVarNum})));
	}
	return 0.0;
    }

    public Set<Assignment> getAssignmentsWithEntry(int varNum, Set<Integer> varValues) {
	assert varNum == childVarNum || varNum == parentVarNum;

	Set<Assignment> possibleAssignments = new HashSet<Assignment>();
	int childVarValue = childVar.getValueIndex(childProbs.keySet().iterator().next());
	if (varNum == childVarNum) {
	    if (varValues.contains(childVarValue)) {
		for (Assignment a : parentFactor.outcomes()) {
		    possibleAssignments.add(a.jointAssignment(new Assignment(childVarNum, childVarValue)));
		}
	    }
	} else {
	    for (Assignment a : parentFactor.getAssignmentsWithEntry(varNum, varValues)) {
		possibleAssignments.add(a.jointAssignment(new Assignment(childVarNum, childVarValue)));
	    }
	}
	return possibleAssignments;
    }

    //////////////////////////////////////////////////////////////
    // Efficiency methods
    //////////////////////////////////////////////////////////////

    /**
     * Re-implementation of sum-product to be aware of the CFG parse chart.
     */
    public DiscreteFactor sumProduct(List<DiscreteFactor> inboundMessages, Collection<Integer> variablesToRetain) {
	if (variablesToRetain.size() <= 1) {
	    return parentFactor.sumProduct(inboundMessages, variablesToRetain);
	} else {
	    return new ChartFactor(chart, parentVar, childVar, parentVarNum, childVarNum,
		    childProbs, parentFactor.sumProduct(inboundMessages, variablesToRetain));
	}
    }

    /**
     * Re-implementation of max-product to be aware of the CFG parse chart.
     */
    public DiscreteFactor maxProduct(List<DiscreteFactor> inboundMessages, Collection<Integer> variablesToRetain) {
	if (variablesToRetain.size() <= 1) {
	    return parentFactor.maxProduct(inboundMessages, variablesToRetain);
	} else {
	    return new ChartFactor(chart, parentVar, childVar, parentVarNum, childVarNum,
		    childProbs, parentFactor.maxProduct(inboundMessages, variablesToRetain));
	}
    }

    /**
     * Marginalization on this factor can only correspond to retaining both parent and child, or
     * retaining just the parent.
     */
    protected DiscreteFactor marginalize(Collection<Integer> varNumsToEliminate, boolean useSum) {
	Set<Integer> varsToRetain = new HashSet<Integer>();
	varsToRetain.addAll(getVarNums());
	varsToRetain.removeAll(varNumsToEliminate);
	
	if (useSum) {
	    return sumProduct(Collections.EMPTY_LIST, varsToRetain);
	} else {
	    return maxProduct(Collections.EMPTY_LIST, varsToRetain);
	}
    }

    private class AssignmentWrapperIterator implements Iterator<Assignment> {

	private int value;
	private int varNum;
	private Iterator<Assignment> baseIter;

	public AssignmentWrapperIterator(int varNum, int value, Iterator<Assignment> baseIter) {
	    this.varNum = varNum;
	    this.value = value;
	    this.baseIter = baseIter;
	}
	
	public boolean hasNext() {
	    return baseIter.hasNext();
	}

	public Assignment next() {
	    return baseIter.next().jointAssignment(new Assignment(varNum, value));
	}

	public void remove() {
	    throw new UnsupportedOperationException();
	}
    }

}