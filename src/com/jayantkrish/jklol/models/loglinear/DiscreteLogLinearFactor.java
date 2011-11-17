package com.jayantkrish.jklol.models.loglinear;

import java.util.Iterator;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.AbstractParametricFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.util.AllAssignmentIterator;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A {@link LogLinearFactor} over {@link DiscreteVariable}s.
 */
public class DiscreteLogLinearFactor extends AbstractParametricFactor<SufficientStatistics> {

  private final ImmutableList<FeatureFunction> myFeatures;

  public DiscreteLogLinearFactor(VariableNumMap vars, List<FeatureFunction> features) {
    super(vars);
    myFeatures = ImmutableList.copyOf(features);
  }

  // ///////////////////////////////////////////////////////////
  // Required methods for ParametricFactor
  // ///////////////////////////////////////////////////////////

  @Override
  public TableFactor getFactorFromParameters(SufficientStatistics parameters) {
    FeatureSufficientStatistics featureParameters = parameters.coerceToFeature();
    Preconditions.checkArgument(featureParameters.getFeatures().size() == myFeatures.size());

    // TODO(jayantk): This is probably not the most efficient way to build this
    // factor.
    TableFactorBuilder builder = new TableFactorBuilder(getVars());
    Iterator<Assignment> allAssignmentIter = new AllAssignmentIterator(getVars());
    while (allAssignmentIter.hasNext()) {
      builder.setWeight(allAssignmentIter.next(), 1.0);
    }
    
    double[] featureWeights = featureParameters.getWeights();
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
  public FeatureSufficientStatistics getNewSufficientStatistics() {
    return new FeatureSufficientStatistics(myFeatures);
  }

  @Override
  public void incrementSufficientStatisticsFromAssignment(SufficientStatistics statistics, Assignment assignment, double count) {
    Preconditions.checkArgument(assignment.containsAll(getVars().getVariableNums()));
    Assignment subAssignment = assignment.intersection(getVars().getVariableNums());
    FeatureSufficientStatistics featureStats = statistics.coerceToFeature();
    double[] weights = featureStats.getWeights();
    Preconditions.checkArgument(weights.length == myFeatures.size());
    for (int i = 0; i < myFeatures.size(); i++) {
      weights[i] += count * myFeatures.get(i).getValue(subAssignment);
    }
  }

  @Override
  public void incrementSufficientStatisticsFromMarginal(SufficientStatistics statistics, 
      Factor marginal, Assignment conditionalAssignment, double count, double partitionFunction) {
    FeatureSufficientStatistics featureStats = statistics.coerceToFeature();
    double[] weights = featureStats.getWeights();
    Preconditions.checkArgument(weights.length == myFeatures.size());
   
    for (int i = 0; i < myFeatures.size(); i++) {
      weights[i] += myFeatures.get(i).computeExpectation(marginal, conditionalAssignment) * 
          count / partitionFunction;
    }
  }

  // ////////////////////////////////////////////////////////////
  // Other methods
  // ////////////////////////////////////////////////////////////

  /**
   * Gets the features which are the parameterization of this factor.
   */
  public List<FeatureFunction> getFeatures() {
    return myFeatures;
  }

  /**
   * Creates and returns a {@code DiscreteLogLinearFactor} over {@code vars}
   * which is parameterized by indicator functions. The returned factor has one
   * indicator feature for every possible assignment to {@code vars}.
   *
   * {@code vars} must contain only {@link DiscreteVariable}s.
   * 
   * @param vars
   * @return
   */
  public static DiscreteLogLinearFactor createIndicatorFactor(VariableNumMap vars) {
    Preconditions.checkArgument(vars.size() == vars.getDiscreteVariables().size());
    List<FeatureFunction> features = Lists.newArrayList();
    Iterator<Assignment> iter = new AllAssignmentIterator(vars);
		while (iter.hasNext()) {
		  features.add(new IndicatorFeatureFunction(iter.next()));
		}
		return new DiscreteLogLinearFactor(vars, features);
  }
}
