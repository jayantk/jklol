
package com.jayantkrish.jklol.cfg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.util.Assignment;


/**
 * A ChartFactor represents conditional distributions over the root variable of 
 * CFG parses.
 */
public class ChartFactor extends DiscreteFactor {

	private ParseChart chart;
	private int parentVarNum;
	private int childVarNum;
	private DiscreteVariable parentVar;
	private DiscreteVariable childVar;

	private DiscreteFactor parentFactor;
	private Map<List<Production>, Double> childProbs;

	public ChartFactor(ParseChart chart, DiscreteVariable parentVar, 
			DiscreteVariable childVar, int parentVarNum, int childVarNum, 
			Map<List<Production>, Double> childProbs) {
		super(new VariableNumMap(Arrays.asList(new Integer[] {parentVarNum, childVarNum}), 
				Arrays.asList(new DiscreteVariable[] {parentVar, childVar})));

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
		TableFactor tempFactor = new TableFactor(new VariableNumMap(
				Arrays.asList(new Integer[] {parentVarNum}), 
				Arrays.asList(new DiscreteVariable[] {parentVar})));
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
	public ChartFactor(ParseChart chart, DiscreteVariable parentVar, DiscreteVariable childVar,
			int parentVarNum, int childVarNum, Map<List<Production>, Double> childProbs, DiscreteFactor parentFactor) {
		super(new VariableNumMap(Arrays.asList(new Integer[] {parentVarNum, childVarNum}), 
				Arrays.asList(new DiscreteVariable[] {parentVar, childVar})));

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
			marginal.put((Production) a.getVarValue(parentVarNum), parentFactor.getUnnormalizedProbability(a));
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
		if (childProbs.containsKey(a.getVarValue(childVarNum))) {
			return parentFactor.getUnnormalizedProbability(
					a.subAssignment(Arrays.asList(new Integer[] {parentVarNum})));
		}
		return 0.0;
	}

	public Set<Assignment> getAssignmentsWithEntry(int varNum, Set<Object> varValues) {
		assert varNum == childVarNum || varNum == parentVarNum;

		Set<Assignment> possibleAssignments = new HashSet<Assignment>();
		List<Production> childVarValue = childProbs.keySet().iterator().next();
		if (varNum == childVarNum) {
			if (varValues.contains(childVarValue)) {
				Iterator<Assignment> assignmentIter = parentFactor.outcomeIterator();
				while (assignmentIter.hasNext()) {
					possibleAssignments.add(assignmentIter.next().jointAssignment(new Assignment(childVarNum, childVarValue)));
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

	// TODO(jayant): These methods are going to horribly break junction tree due to
	// refactoring the factor product methods out of individual factors.

	/**
	 * Re-implementation of sum-product to be aware of the CFG parse chart.
	 */
	public DiscreteFactor sumProduct(List<DiscreteFactor> inboundMessages, Collection<Integer> variablesToRetain) {
		List<DiscreteFactor> allFactors = new ArrayList<DiscreteFactor>(inboundMessages);
		allFactors.add(parentFactor);
		if (variablesToRetain.size() <= 1) {
			return TableFactor.sumProductTableFactor(allFactors, variablesToRetain);
		} else {
			return new ChartFactor(chart, parentVar, childVar, parentVarNum, childVarNum,
					childProbs, TableFactor.sumProductTableFactor(allFactors, variablesToRetain));
		}
	}

	/**
	 * Re-implementation of max-product to be aware of the CFG parse chart.
	 */
	public DiscreteFactor maxProduct(List<DiscreteFactor> inboundMessages, Collection<Integer> variablesToRetain) {
		List<DiscreteFactor> allFactors = new ArrayList<DiscreteFactor>(inboundMessages);
		allFactors.add(parentFactor);
		if (variablesToRetain.size() <= 1) {
			return TableFactor.maxProductTableFactor(allFactors, variablesToRetain);
		} else {
			return new ChartFactor(chart, parentVar, childVar, parentVarNum, childVarNum,
					childProbs, TableFactor.maxProductTableFactor(allFactors, variablesToRetain));
		}
	}

	/**
	 * Marginalization on this factor can only correspond to retaining both parent and child, or
	 * retaining just the parent.
	 */
	protected DiscreteFactor marginalize(Collection<Integer> varNumsToEliminate, boolean useSum) {
		Set<Integer> varsToRetain = new HashSet<Integer>();
		varsToRetain.addAll(getVars().getVariableNums());
		varsToRetain.removeAll(varNumsToEliminate);

		List<DiscreteFactor> emptyList = Collections.emptyList();
		if (useSum) {
			return sumProduct(emptyList, varsToRetain);
		} else {
			return maxProduct(emptyList, varsToRetain);
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