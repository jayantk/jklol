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

public class InnerProductParametricFactor extends AbstractParametricFactor {
  private static final long serialVersionUID = 1L;

  private final Assignment assignment;
  private final int dimensionality;

  public InnerProductParametricFactor(VariableNumMap variables,
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
      double assignmentWeight = Math.exp(params1.innerProduct(params2));

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
      
      List<SufficientStatistics> parameterList = currentParameters.coerceToList().getStatistics();
      SufficientStatistics params1 = parameterList.get(0);
      SufficientStatistics params2 = parameterList.get(1);

      gradient1.increment(params2, count);
      gradient2.increment(params1, count);
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
    return new ListSufficientStatistics(Arrays.asList("0", "1"), vectors);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) {
    StringBuilder sb = new StringBuilder();
    sb.append(parameters.getDescription());

    return sb.toString();
  }
}
