package com.jayantkrish.jklol.inference;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.jayantkrish.jklol.models.Factor;

/**
 * Stores a set of {@link Factor}s representing marginal distributions and uses them
 * to answer queries for marginals.
 * @author jayant
 *
 */
public class FactorMarginalSet implements MarginalSet {

	private final Multimap<Integer, Factor> variableFactorMap;
	private final double partitionFunction;
	
	public FactorMarginalSet(List<Factor> factors, double partitionFunction) {
    this.partitionFunction = partitionFunction;
		this.variableFactorMap = HashMultimap.create();
		for (Factor factor : factors) {
			for (Integer variableNum : factor.getVars().getVariableNums()) {
				variableFactorMap.put(variableNum, factor);
			}
		}
	}
	
	@Override
	public Factor getMarginal(Collection<Integer> varNums) {
		// Find a factor among the given factors that includes all of the given variables.
		Set<Factor> relevantFactors = null;
		Set<Integer> varNumsToRetain = new HashSet<Integer>();
		for (Integer varNum : varNums) {
			varNumsToRetain.add(varNum);
			if (relevantFactors == null) {
				relevantFactors = new HashSet<Factor>(variableFactorMap.get(varNum));
			} else {
				relevantFactors.retainAll(variableFactorMap.get(varNum));
			}
		}

		if (relevantFactors.size() == 0) {
			throw new RuntimeException("Graph does not contain a factor with all variables: " + varNums);
		}

		// Pick an arbitrary factor to use for the marginal
		Factor marginal = relevantFactors.iterator().next();

		// Marginalize out any remaining variables...
		Set<Integer> allVarNums = new HashSet<Integer>(marginal.getVars().getVariableNums());
		allVarNums.removeAll(varNumsToRetain);
		return marginal.marginalize(allVarNums);
	}
	
	@Override
	public double getPartitionFunction() {
		return partitionFunction;
	}
}
