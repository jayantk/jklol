package com.jayantkrish.jklol.models.loglinear;

import java.util.Arrays;
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
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.models.parametric.TensorSufficientStatistics;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.tensor.TensorBuilder;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A {@link ParametricFactor} whose parameters are weights of log-linear feature
 * functions.
 * 
 * {@code DiscreteLogLinearFactor} can represent sparse factors (with 0
 * probability outcomes) through a set of initial weights for the returned
 * factor. Each initial weight should be set to either 0 or 1, and outcomes with
 * 0 weight will retain that weight regardless of their feature values.
 */
public class DiscreteLogLinearFactor extends AbstractParametricFactor<SufficientStatistics> {

  private final ImmutableList<FeatureFunction> features;
  private final TableFactorBuilder initialWeights;

  /**
   * Creates a {@code DiscreteLogLinearFactor} over {@code variables},
   * parameterized by {@code features}. The returned factor is dense, and
   * constructs factors that assign positive probability to all possible
   * assignments.
   * 
   * @param vars
   * @param features
   */
  public DiscreteLogLinearFactor(VariableNumMap variables, List<FeatureFunction> features) {
    super(variables);
    this.features = ImmutableList.copyOf(features);
    this.initialWeights = TableFactorBuilder.ones(variables);
  }

  /**
   * Creates a {@code DiscreteLogLinearFactor} over {@code variables},
   * parameterized by {@code features}. The returned factor is sparse, and
   * assignments with a 0 weight in {@code initialWeights} will be assigned 0
   * weight in all constructed factors.
   * 
   * @param vars
   * @param features
   */
  public DiscreteLogLinearFactor(VariableNumMap variables, List<FeatureFunction> features,
      TableFactorBuilder initialWeights) {
    super(variables);
    this.features = ImmutableList.copyOf(features);
    this.initialWeights = initialWeights;
  }

  // ///////////////////////////////////////////////////////////
  // Required methods for ParametricFactor
  // ///////////////////////////////////////////////////////////

  @Override
  public TableFactor getFactorFromParameters(SufficientStatistics parameters) {
    // TODO(jayantk): This is probably not the most efficient way to build this
    // factor.
    TableFactorBuilder builder = new TableFactorBuilder(initialWeights);
    TensorBuilder featureWeights = getFeatureWeights(parameters);

    for (int i = 0; i < features.size(); i++) { 
      FeatureFunction feature = features.get(i);
      Iterator<Assignment> iter = feature.getNonzeroAssignments();
      int[] index = new int[] { i };
      while (iter.hasNext()) {
        Assignment assignment = iter.next();
        builder.multiplyWeight(assignment, Math.exp(featureWeights.getByDimKey(index) * feature.getValue(assignment)));
      }
    }
    return builder.build();
  }

  @Override
  public TensorSufficientStatistics getNewSufficientStatistics() {
    return new TensorSufficientStatistics(Arrays.<TensorBuilder> asList(new DenseTensorBuilder(new int[] { 0 }, new int[] { features.size() })));
  }

  @Override
  public void incrementSufficientStatisticsFromAssignment(SufficientStatistics statistics, Assignment assignment, double count) {
    Preconditions.checkArgument(assignment.containsAll(getVars().getVariableNums()));
    Assignment subAssignment = assignment.intersection(getVars().getVariableNums());

    TensorBuilder weights = getFeatureWeights(statistics);
    for (int i = 0; i < features.size(); i++) {
      weights.incrementEntry(count * features.get(i).getValue(subAssignment), i);
    }
  }

  @Override
  public void incrementSufficientStatisticsFromMarginal(SufficientStatistics statistics,
      Factor marginal, Assignment conditionalAssignment, double count, double partitionFunction) {
    TensorBuilder weights = getFeatureWeights(statistics);

    for (int i = 0; i < features.size(); i++) {
      double featureExpectation = features.get(i).computeExpectation(marginal, conditionalAssignment) *
          count / partitionFunction;
      weights.incrementEntry(featureExpectation, i);
    }
  }

  private TensorBuilder getFeatureWeights(SufficientStatistics parameters) {
    TensorSufficientStatistics featureParameters = (TensorSufficientStatistics) parameters;
    // Check that the parameters are a vector of the appropriate size.
    Preconditions.checkArgument(featureParameters.size() == 1);
    Preconditions.checkArgument(featureParameters.get(0).getDimensionNumbers().length == 1);
    Preconditions.checkArgument(featureParameters.get(0).getDimensionSizes()[0] == features.size());
    return featureParameters.get(0);
  }

  // ////////////////////////////////////////////////////////////
  // Other methods
  // ////////////////////////////////////////////////////////////

  /**
   * Gets the features which are the parameterization of this factor.
   */
  public List<FeatureFunction> getFeatures() {
    return features;
  }

  /**
   * Creates and returns a {@code DiscreteLogLinearFactor} over {@code vars}
   * which is parameterized by indicator functions. The returned factor has one
   * indicator feature for every possible assignment to {@code vars}.
   * {@code initialWeights} determines the sparsity pattern of the factors
   * created by the returned parametric factor; use
   * {@link TableFactorBuilder#ones} to create a dense factor.
   * 
   * {@code vars} must contain only {@link DiscreteVariable}s.
   * 
   * @param vars
   * @return
   */
  public static DiscreteLogLinearFactor createIndicatorFactor(VariableNumMap vars,
      TableFactorBuilder initialWeights) {
    Preconditions.checkArgument(vars.size() == vars.getDiscreteVariables().size());
    List<FeatureFunction> features = Lists.newArrayList();
    // Only add features for outcomes with nonzero weight.
    Iterator<Assignment> iter = initialWeights.assignmentIterator();
    while (iter.hasNext()) {
      features.add(new IndicatorFeatureFunction(iter.next()));
    }
    return new DiscreteLogLinearFactor(vars, features, initialWeights);
  }

  /**
   * Same as {@link #createIndicatorFactor(VariableNumMap, TableFactorBuilder)},
   * using a the all-ones factor builder.
   * 
   * @param vars
   * @return
   */
  public static DiscreteLogLinearFactor createIndicatorFactor(VariableNumMap vars) {
    return DiscreteLogLinearFactor.createIndicatorFactor(vars, TableFactorBuilder.ones(vars));
  }
}
