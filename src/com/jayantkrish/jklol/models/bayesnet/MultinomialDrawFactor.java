package com.jayantkrish.jklol.models.bayesnet;

import java.util.Collection;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.jayantkrish.jklol.models.AbstractFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.IndependentProductFactor;
import com.jayantkrish.jklol.models.RealVariable;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.loglinear.FeatureFunction;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.Vector;

public class MultinomialDrawFactor extends AbstractFactor {

	private int realVarNum;
	private RealVariable realVar;
	private int discreteVarNum;
	private DiscreteVariable discreteVar;
	
	public MultinomialDrawFactor(VariableNumMap vars) {
		super(vars);
		Preconditions.checkArgument(vars.size() == 2);
		Preconditions.checkArgument(vars.getDiscreteVariables().size() == 1);
		Preconditions.checkArgument(vars.getRealVariables().size() == 1);
		Preconditions.checkArgument(vars.getDiscreteVariables().get(0).numValues()
				== vars.getRealVariables().get(0).numDimensions());
	}
	
	@Override
	public double computeExpectation(FeatureFunction feature) {
		throw new UnsupportedOperationException("Not yet implemented.");
	}

	@Override
	public Factor conditional(Assignment a) {
		Assignment factorSubset = a.subAssignment(getVars().getVariableNums());
		if (factorSubset.getVarNumsSorted().size() == 0) {
			return this;
		}

		VariableNumMap realVars = getVars().intersection(ImmutableList.of(realVarNum));
		VariableNumMap discreteVars = getVars().intersection(ImmutableList.of(discreteVarNum));
		
		Factor continuousPart = null;
		if (factorSubset.containsVar(realVarNum)) {
			TableFactor continuousPartInit = new TableFactor(realVars);
			continuousPartInit.setWeight(a.subAssignment(ImmutableList.of(realVarNum)), 1.0);
			continuousPart = continuousPartInit;
		} else {
			Vector parameters = Vector.constantVector(realVar.numDimensions(), 1.0);
			parameters.addTo(discreteVar.getValueIndex(a.getVarValue(discreteVarNum)), 1.0);
			continuousPart = new DirichletFactor(realVars, parameters);
		}
		
		TableFactor discretePart = new TableFactor(discreteVars);
		if (factorSubset.containsVar(discreteVarNum)) {
			discretePart.setWeight(a.subAssignment(ImmutableList.of(discreteVarNum)), 1.0);
		} else {
			Vector realVectorValue = (Vector) a.getVarValue(realVarNum);
			for (int i = 0; i < realVectorValue.numDimensions(); i++) {
				Assignment ithAssignment = new Assignment(ImmutableList.of(discreteVarNum), 
						ImmutableList.of(discreteVar.getValue(i)));
				discretePart.setWeight(ithAssignment, realVectorValue.get(i));
			}
		}
				
		return new IndependentProductFactor(ImmutableList.of(continuousPart, discretePart));
	}
	
	@Override
	public double getUnnormalizedProbability(Assignment assignment) {
		int valueNum = discreteVar.getValueIndex(assignment.getVarValue(discreteVarNum));
		return ((Vector) assignment.getVarValue(realVarNum)).get(valueNum);
	}

	@Override
	public double getPartitionFunction() {
		throw new UnsupportedOperationException("Not implemented");
	}
	
	@Override
	public Factor marginalize(Collection<Integer> varNumsToEliminate) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public Factor maxMarginalize(Collection<Integer> varNumsToEliminate) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public Assignment sample() {
		throw new UnsupportedOperationException("Not implemented");
	}

}
