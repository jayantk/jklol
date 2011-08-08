package com.jayantkrish.jklol.models;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.loglinear.FeatureFunction;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.Pair;
import com.jayantkrish.jklol.util.PairComparator;

/**
 * DiscreteFactor provides a generic implementation of most methods of the {@link Factor} interface for
 * variables which take on discrete values.
 */
public abstract class DiscreteFactor extends AbstractFactor {

	private double partitionFunction;

	/**
	 * DiscreteFactor must be defined only over {@link DiscreteVariable}s. Throws an 
	 * IllegalArgumentException if vars contains anything but DiscreteVariables.
	 * @param vars
	 */
	public DiscreteFactor(VariableNumMap vars) {
		super(vars);
		Preconditions.checkArgument(vars.getDiscreteVariables().size() == vars.size());
		
		this.partitionFunction = -1.0;
	}

	//////////////////////////////////////////////////////////////////////////
	// Additional methods provided by DiscreteFactors which are not provided by Factor.
	//////////////////////////////////////////////////////////////////////////

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
	 * Get all assignments to this variable with nonzero probability that contain the specified
	 * variable number -> (any value in varValues) mapping.
	 *
	 * The default implementation of this method is fairly inefficient -- overriding it 
	 * with a more efficient implementation should significantly improve performance.
	 */
	public Set<Assignment> getAssignmentsWithEntry(int varNum, Set<Object> varValues) {
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

	/**
	 * Get most likely assignments.
	 */
	public List<Assignment> mostLikelyAssignments(int numAssignments) {
		Iterator<Assignment> iter = outcomeIterator();
		PriorityQueue<Pair<Double, Assignment>> pq = 
			new PriorityQueue<Pair<Double, Assignment>>(
					numAssignments, new PairComparator<Double, Assignment>());

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

	///////////////////////////////////////////////////////////////////////////////////
	// Methods for performing inference = methods from Factor
	///////////////////////////////////////////////////////////////////////////////////
	
	@Override
	public Factor conditional(Assignment a) {
		VariableNumMap factorVars = getVars().intersection(a.getVarNumsSorted());
		Assignment subAssignment = a.subAssignment(factorVars);
		TableFactor tableFactor = new TableFactor(factorVars);
		tableFactor.setWeight(subAssignment, 1.0);
		return this.product(tableFactor);
	}

	public DiscreteFactor marginalize(Collection<Integer> varNumsToEliminate) {
		return marginalize(varNumsToEliminate, true);
	}

	public DiscreteFactor maxMarginalize(Collection<Integer> varNumsToEliminate) {
		return marginalize(varNumsToEliminate, false);
	}

	/**
	 * Sums or maximizes out a particular set of variables. If useSum is true,
	 * probabilities are summed.
	 */
	protected DiscreteFactor marginalize(Collection<Integer> varNumsToEliminate, boolean useSum) {
		Set<Integer> varNumsToEliminateSet = new HashSet<Integer>(varNumsToEliminate);

		VariableNumMap retainedVars = getVars().removeAll(varNumsToEliminateSet);
		TableFactor returnFactor = new TableFactor(retainedVars);

		Iterator<Assignment> outcomeIterator = outcomeIterator();
		while (outcomeIterator.hasNext()) {
			Assignment assignment = outcomeIterator.next();
			Assignment subAssignment = assignment.subAssignment(retainedVars.getVariableNums());

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
	
	@Override
	public Factor add(Factor other) {
		DiscreteFactor otherAsDiscrete = other.coerceToDiscrete();
		return TableFactor.sumFactor(this, otherAsDiscrete);
	}
	
	@Override
	public Factor product(Factor other) {
		DiscreteFactor otherAsDiscrete = other.coerceToDiscrete();
		return TableFactor.productFactor(this, otherAsDiscrete);
	}
	
	/*
	 * (non-Javadoc) This is more efficient than the default
	 * implementation in {@code AbstractFactor}.
	 *    
	 * @see com.jayantkrish.jklol.models.AbstractFactor#product(java.util.List)
	 */
	@Override
	public Factor product(List<Factor> others) {
		List<DiscreteFactor> discreteFactors = Lists.newArrayList();
		discreteFactors.add(this);
		for (Factor other : others) {
			discreteFactors.add(other.coerceToDiscrete());
		}
		return TableFactor.productFactor(discreteFactors);
	}
	
	@Override
	public Factor product(double constant) {
		return TableFactor.productFactor(this, constant);
	}
	
	@Override
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
		
		if (a == null) {
			// If we didn't draw a sample, fail early.
			throw new IllegalStateException("Could not sample from DiscreteFactor." + this + " : " + sumProb);
		}
		return a;	
	}

	@Override
	public DiscreteFactor coerceToDiscrete() {
		return this;
	}
	
	/**
	 * Get the partition function = denominator = total sum probability of all assignments.
	 */ 
	private double getPartitionFunction() {
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
}