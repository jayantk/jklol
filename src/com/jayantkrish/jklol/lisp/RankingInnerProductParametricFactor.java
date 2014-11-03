package com.jayantkrish.jklol.lisp;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.AbstractParametricFactor;
import com.jayantkrish.jklol.models.parametric.ListSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.models.parametric.TensorSufficientStatistics;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.util.Assignment;

/**
 * An inner product classifier used for ranking a pair of items. 
 * The weight assigned to an outcome is given by
 * {@code theta^T (x_1 - x_2)}, where theta is a parameter vector,
 * and x_1 and x_2 are parameter vectors for the two items.
 * 
 * @author jayantk
 */
public class RankingInnerProductParametricFactor extends AbstractParametricFactor {
  private static final long serialVersionUID = 1L;

  private final Assignment assignment;
  private final int dimensionality;

  public RankingInnerProductParametricFactor(VariableNumMap variables,
      Assignment assignment, int dimensionality) {
    super(variables);
    this.assignment = Preconditions.checkNotNull(assignment);
    Preconditions.checkArgument(variables.containsAll(assignment.getVariableNumsArray()));
    this.dimensionality = dimensionality;
  }

  @Override
  public Factor getModelFromParameters(SufficientStatistics parameters) {
    if (getVars().isValidAssignment(assignment)) {
      List<SufficientStatistics> parameterList = parameters.coerceToList().getStatistics();
      SufficientStatistics params1 = parameterList.get(0);
      SufficientStatistics params2 = parameterList.get(1);
      SufficientStatistics params3 = parameterList.get(2);
      double assignmentWeight = Math.exp(params1.innerProduct(params2) - params1.innerProduct(params3));

      TableFactorBuilder builder = TableFactorBuilder.ones(getVars());
      builder.setWeight(assignment, assignmentWeight);
      return builder.build();
    } else {
      return TableFactor.unity(getVars());
    }
  }

  @Override
  public void incrementSufficientStatisticsFromAssignment(SufficientStatistics gradient,
      SufficientStatistics currentParameters, Assignment givenAssignment, double count) {
    if (givenAssignment.equals(assignment)) {
      List<SufficientStatistics> gradientList = gradient.coerceToList().getStatistics();
      SufficientStatistics gradient1 = gradientList.get(0);
      SufficientStatistics gradient2 = gradientList.get(1);
      SufficientStatistics gradient3 = gradientList.get(2);
      
      List<SufficientStatistics> parameterList = currentParameters.coerceToList().getStatistics();
      SufficientStatistics params1 = parameterList.get(0);
      SufficientStatistics params2 = parameterList.get(1);
      SufficientStatistics params3 = parameterList.get(2);

      gradient1.increment(params2, count);
      gradient1.increment(params3, -1.0 * count);
      gradient2.increment(params1, count);
      gradient3.increment(params1, -1.0 * count);
    }
  }

  @Override
  public void incrementSufficientStatisticsFromMarginal(SufficientStatistics gradient,
      SufficientStatistics currentParameters, Factor marginal, Assignment conditionalAssignment,
      double count, double partitionFunction) {

    Assignment intersection = assignment.intersection(
        conditionalAssignment.getVariableNumsArray());
    if (intersection.equals(conditionalAssignment)) {
      Assignment factorAssignment = assignment.removeAll(
          conditionalAssignment.getVariableNumsArray());
      
      double probability = marginal.getUnnormalizedProbability(factorAssignment);

      incrementSufficientStatisticsFromAssignment(gradient, currentParameters, assignment,
          count * probability / partitionFunction);
    }
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    VariableNumMap vars = getVars();
    List<SufficientStatistics> vectors = Lists.newArrayList();
    vectors.add(TensorSufficientStatistics.createDense(vars, new DenseTensorBuilder(
        new int[] {0}, new int[] {dimensionality})));
    vectors.add(TensorSufficientStatistics.createDense(vars, new DenseTensorBuilder(
        new int[] {0}, new int[] {dimensionality})));
    vectors.add(TensorSufficientStatistics.createDense(vars, new DenseTensorBuilder(
        new int[] {0}, new int[] {dimensionality})));
    return new ListSufficientStatistics(Arrays.asList("0", "1", "2"), vectors);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) {
    StringBuilder sb = new StringBuilder();
    sb.append(parameters.getDescription());
    return sb.toString();
  }
}
