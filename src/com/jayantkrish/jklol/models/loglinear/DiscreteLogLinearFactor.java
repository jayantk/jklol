package com.jayantkrish.jklol.models.loglinear;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.AbstractParametricFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A {@link LogLinearFactor} over {@link DiscreteVariable}s.
 */ 
public class DiscreteLogLinearFactor extends AbstractParametricFactor<SufficientStatistics> {

	private final ImmutableList<FeatureFunction> myFeatures;

	public DiscreteLogLinearFactor(VariableNumMap vars, List<FeatureFunction> features) {
		super(vars);
		myFeatures = ImmutableList.copyOf(new HashSet<FeatureFunction>());
	}

	/////////////////////////////////////////////////////////////
	// Required methods for ParametricFactor
	/////////////////////////////////////////////////////////////

	@Override
	public TableFactor getFactorFromParameters(SufficientStatistics parameters) {
	  FeatureSufficientStatistics featureParameters = parameters.coerceToFeature();
	  Preconditions.checkArgument(featureParameters.getFeatures().size() == myFeatures.size());

	  // TODO(jayantk): This is probably not the most efficient way to build this factor.
	  double[] featureWeights = featureParameters.getWeights();
	  TableFactorBuilder builder = new TableFactorBuilder(getVars());
	  for (int i = 0; i < myFeatures.size(); i++) {
	    FeatureFunction feature = myFeatures.get(i);
	    Iterator<Assignment> iter = feature.getNonzeroAssignments();
	    while (iter.hasNext()) {
	      Assignment assignment = iter.next();
	      builder.multiplyWeight(assignment, Math.exp(featureWeights[i] * feature.getValue(assignment)));
	    }
	  }
	  return builder.build();
	}

	@Override
	public SufficientStatistics getNewSufficientStatistics() {
	  return new FeatureSufficientStatistics(myFeatures);
	}
	
	@Override
	public SufficientStatistics getSufficientStatisticsFromAssignment(Assignment assignment, double count) {
	  double[] weights = new double[myFeatures.size()];
	  for (int i = 0; i < myFeatures.size(); i++) {	  
	    weights[i] = count * myFeatures.get(i).getValue(assignment); 
	  }
	  return new FeatureSufficientStatistics(myFeatures, weights);
	}
	
	@Override
	public SufficientStatistics getSufficientStatisticsFromMarginal(Factor marginal, double count, double partitionFunction) {
	  double[] weights = new double[myFeatures.size()];
	  for (int i = 0; i < myFeatures.size(); i++) {	  
	    weights[i] = count * marginal.computeExpectation(myFeatures.get(i)) / partitionFunction; 
	  }
	  return new FeatureSufficientStatistics(myFeatures, weights);
	}
	
	//////////////////////////////////////////////////////////////
	// Other methods 
	//////////////////////////////////////////////////////////////

	/**
	 * Gets the features which are the parameterization of this factor.
	 */
	public List<FeatureFunction> getFeatures() {
		return myFeatures;
	}
}
