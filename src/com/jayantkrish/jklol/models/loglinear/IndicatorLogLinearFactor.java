package com.jayantkrish.jklol.models.loglinear;

import java.util.Arrays;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.AbstractParametricFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.models.parametric.TensorSufficientStatistics;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.tensor.TensorBuilder;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A {@link ParametricFactor} whose parameters are weights of log-linear
 * indicator features. This class is a less flexible version of
 * {@code DiscreteLogLinearFactor}, since it only allows indicator features.
 * However, it uses a more efficient encoding of features that enables it to
 * work with larger feature tensors.
 * 
 * {@code IndicatorLogLinearFactor} can represent sparse factors (with 0
 * probability outcomes) through a set of initial weights for the returned
 * factor. Each initial weight should be set to either 0 or 1, and outcomes with
 * 0 weight will retain that weight regardless of their feature values.
 */
public class IndicatorLogLinearFactor extends AbstractParametricFactor {

  // Contains a sparsity pattern which must be maintained by this tensor. (i.e.,
  // a set of 0 probability assignments). If null, then no sparsity pattern is
  // enforced.
  private final TableFactor initialWeights;

  /**
   * Creates a {@code IndicatorLogLinearFactor} over {@code variables},
   * parameterized by {@code features}. The returned factor is sparse, and
   * assignments with a 0 weight in {@code initialWeights} will be assigned 0
   * weight in all constructed factors.
   * 
   * @param vars
   * @param features
   */
  public IndicatorLogLinearFactor(VariableNumMap variables, TableFactor initialWeights) {
    super(variables);
    this.initialWeights = Preconditions.checkNotNull(initialWeights);
  }

  public TableFactor getFeatureValues() {
    return initialWeights;
  }

  // ///////////////////////////////////////////////////////////
  // Required methods for ParametricFactor
  // ///////////////////////////////////////////////////////////

  @Override
  public TableFactor getFactorFromParameters(SufficientStatistics parameters) {
    Tensor featureWeights = getFeatureWeights(parameters).build();

    double[] logProbs = featureWeights.getValues();
    double[] probs = new double[logProbs.length];
    for (int i = 0; i < logProbs.length; i++) {
      probs[i] = Math.exp(logProbs[i]);
    }

    return new TableFactor(initialWeights.getVars(), initialWeights.getWeights().replaceValues(probs));
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters) {
    throw new UnsupportedOperationException();
  }

  @Override
  public TensorSufficientStatistics getNewSufficientStatistics() {
    return new TensorSufficientStatistics(Arrays.<TensorBuilder> asList(
        new DenseTensorBuilder(new int[] { 0 },
            new int[] { initialWeights.getWeights().getValues().length })));
  }

  @Override
  public void incrementSufficientStatisticsFromAssignment(SufficientStatistics statistics,
      Assignment assignment, double count) {
    Preconditions.checkArgument(assignment.containsAll(getVars().getVariableNums()));
    Assignment subAssignment = assignment.intersection(getVars().getVariableNums());

    long keyNum = initialWeights.getWeights().dimKeyToKeyNum(
        initialWeights.getVars().assignmentToIntArray(subAssignment));
    int index = initialWeights.getWeights().keyNumToIndex(keyNum);

    TensorBuilder weights = getFeatureWeights(statistics);
    weights.incrementEntry(count, index);
  }

  @Override
  public void incrementSufficientStatisticsFromMarginal(SufficientStatistics statistics,
      Factor marginal, Assignment conditionalAssignment, double count, double partitionFunction) {
    TensorBuilder weights = getFeatureWeights(statistics);

    VariableNumMap conditionedVars = initialWeights.getVars().intersection(
        conditionalAssignment.getVariableNums());

    TableFactor productFactor = (TableFactor) initialWeights.product(
        TableFactor.pointDistribution(conditionedVars, conditionalAssignment.intersection(conditionedVars)))
        .product(marginal);

    Tensor productFactorWeights = productFactor.getWeights();
    double[] productFactorValues = productFactorWeights.getValues();
    int tensorSize = productFactorWeights.size();
    double multiplier = count / partitionFunction;
    for (int i = 0; i < tensorSize; i++) {
      int builderIndex = (int) productFactorWeights.indexToKeyNum(i);
      weights.incrementEntry(productFactorValues[i] * multiplier, builderIndex);
    }
  }

  private TensorBuilder getFeatureWeights(SufficientStatistics parameters) {
    TensorSufficientStatistics featureParameters = (TensorSufficientStatistics) parameters;
    // Check that the parameters are a vector of the appropriate size.
    Preconditions.checkArgument(featureParameters.size() == 1);
    Preconditions.checkArgument(featureParameters.get(0).getDimensionNumbers().length == 1);
    Preconditions.checkArgument(featureParameters.get(0).getDimensionSizes()[0] ==
        initialWeights.getWeights().getValues().length);
    return featureParameters.get(0);
  }
}
