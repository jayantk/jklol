package com.jayantkrish.jklol.models.bayesnet;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.AbstractFactor;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.RealVariable;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.loglinear.FeatureFunction;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.GammaMath;
import com.jayantkrish.jklol.util.Vector;

/**
 * DirichletFactor is a Dirichlet distribution over a single real-valued
 * vector variable.
 * @author jayant
 */
public class DirichletFactor extends AbstractFactor {

	private int realVarNum;
	private Vector parameters;
	
	/**
	 * Instantiate a Dirichlet factor with the provided parameters for the
	 * Dirichlet distribution. The ith value of parameters is the exponent for the
	 * ith element of the real valued variable in vars. 
	 * @param vars - A VariableNumMap containing a single real-valued variable.
	 * @param parameters - The Dirichlet distribution parameters. The length of 
	 * parameters must be equal to the number of dimensions of the real-valued variable. 
	 * Every entry of this vector must be greater than 0. 
	 */
	public DirichletFactor(VariableNumMap vars, Vector parameters) {
		super(vars);
		Preconditions.checkArgument(vars.size() == 1);
		Preconditions.checkArgument(vars.getRealVariables().size() == 1);
		Preconditions.checkNotNull(parameters);
		Preconditions.checkArgument(vars.getRealVariables().get(0).numDimensions() == 
			parameters.numDimensions());
		
		realVarNum = vars.getVariableNums().get(0);
		this.parameters = new Vector(parameters);
	}
	
	@Override
	public double computeExpectation(FeatureFunction feature) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	@Override
	public Factor conditional(Assignment a) {
		if (a.containsVar(realVarNum)) {
			TableFactor factor = new TableFactor(getVars());
			factor.setWeight(a, getUnnormalizedProbability(a));
		}
		// Assignment doesn't contain the variable this factor is defined over.
		return this;
	}

	@Override
	public double getPartitionFunction() {
		double denom = 1.0;
		double total = 0.0;
		for (int i = 0; i < parameters.numDimensions(); i++) {
			denom *= GammaMath.gamma(parameters.get(i));
			total += parameters.get(i);
		}
		return denom / GammaMath.gamma(total);
	}

	@Override
	public double getUnnormalizedProbability(Assignment assignment) {
		Preconditions.checkArgument(assignment.containsVar(realVarNum));
		Object objValue = assignment.getVarValue(realVarNum);
		Preconditions.checkArgument(objValue instanceof Vector);

		return getUnnormalizedProbability((Vector) objValue);
	}
	
	public double getUnnormalizedProbability(Vector value) {
		double prob = 1.0;		
		for (int i = 0; i < value.numDimensions(); i++) {
			prob *= Math.pow(value.get(i), parameters.get(i));
		}
		return prob;		
	}

	@Override
	public Factor marginalize(Collection<Integer> varNumsToEliminate) {
		if (varNumsToEliminate.contains(realVarNum)) {
			TableFactor factor = new TableFactor(VariableNumMap.emptyMap());
			factor.setWeight(Assignment.EMPTY, getPartitionFunction());
			return factor;
		}
		return this;
	}

	@Override
	public Factor maxMarginalize(Collection<Integer> varNumsToEliminate) {
		if (varNumsToEliminate.contains(realVarNum)) {
			TableFactor factor = new TableFactor(VariableNumMap.emptyMap());
			factor.setWeight(Assignment.EMPTY, getUnnormalizedProbability(modeVector()));
			return factor;
		}
		return this;
	}
		
	@Override
	public Assignment sample() {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * Get the parameter vector of this Dirichlet distribution.
	 * @return
	 */
	public Vector getDirichletParameters() {
		return parameters;
	}

	/*
	 * Get a vector which is the mode of this distribution. 
	 */
	private Vector modeVector() {
		double[] value = new double[parameters.numDimensions()];
		double denominator = 0.0;
		for (int i = 0; i < parameters.numDimensions(); i++) {
			denominator += parameters.get(i) - 1;
		}
		for (int i = 0; i < value.length; i++) {
			if (parameters.get(i) < 1) {
				value[i] = 0.0;
			} else {
				value[i] = (parameters.get(i) - 1) / denominator;
			}
		}
		return new Vector(value);
	}

	/**
	 * Multiplies together a list of Dirichlet factors. The factors must be defined over
	 * the same random variable.
	 * @param factors
	 * @return
	 */
	public static DirichletFactor productFactor(DirichletFactor ... factors) {
		return productFactor(Arrays.asList(factors));
	}
	
	/**
	 * Multiplies together a list of Dirichlet factors. The factors must be defined over
	 * the same random variable.
	 * @param factors
	 * @return
	 */
	public static DirichletFactor productFactor(List<DirichletFactor> factors) {
		Preconditions.checkNotNull(factors.size());
		VariableNumMap vars = VariableNumMap.emptyMap();
		for (Factor factor : factors) {
			vars = vars.union(factor.getVars());
		}
		Preconditions.checkArgument(vars.size() == 1);
		Preconditions.checkArgument(vars.getRealVariables().size() == 1);
		
		RealVariable var = vars.getRealVariables().get(0);
		Vector newParameters = Vector.constantVector(var.numDimensions(), 0.0);
		for (DirichletFactor factor : factors) {
			newParameters.addTo(factor.getDirichletParameters());
		}
		return new DirichletFactor(vars, newParameters);
	}
}
