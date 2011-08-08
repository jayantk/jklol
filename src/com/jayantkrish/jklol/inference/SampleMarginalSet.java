package com.jayantkrish.jklol.inference;

import java.util.Collection;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A {@link MarginalSet} that computes approximate marginals from a set of
 * samples. These samples are typically drawn from an approximation of a true
 * marginal distribution (for example, from a {@link GibbsSampler}).
 * 
 * @author jayant
 * 
 */
public class SampleMarginalSet implements MarginalSet {

	private final VariableNumMap factorGraphVariables;
	private final ImmutableList<Assignment> samples;

	public SampleMarginalSet(VariableNumMap factorGraphVariables, List<Assignment> samples) {
		this.factorGraphVariables = factorGraphVariables;
		this.samples = ImmutableList.copyOf(samples);
	}

	@Override
	public Factor getMarginal(Collection<Integer> varNums) {
		Preconditions.checkNotNull(varNums);
		VariableNumMap varsToRetain = factorGraphVariables.intersection(varNums);
		TableFactor factor = new TableFactor(varsToRetain);
		for (Assignment sample : samples) {
			Assignment factorSample = sample.subAssignment(varNums);
			factor.setWeight(factorSample, factor
					.getUnnormalizedProbability(factorSample) + 1.0);
		}
		return factor;
	}
	
	@Override
	public double getPartitionFunction() {
		return samples.size();
	}
}
