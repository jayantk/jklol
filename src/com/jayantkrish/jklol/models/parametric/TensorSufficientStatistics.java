package com.jayantkrish.jklol.models.parametric;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.tensor.Tensor;

public class TensorSufficientStatistics implements SufficientStatistics {
  
  private final VariableNumMap statisticNames;
  private Tensor statistics;

  /**
   * Version 1.0
   */
  private static final long serialVersionUID = 1L;

  public TensorSufficientStatistics(VariableNumMap statisticNames, Tensor statistics) {
    Preconditions.checkArgument(statisticNames.getDiscreteVariables().size()
        == statisticNames.size());
    Preconditions.checkArgument(Ints.asList(statistics.getDimensionNumbers())
        .equals(statisticNames.getVariableNums()));

    this.statisticNames = statisticNames;
    this.statistics = statistics;
  }
  
  public Tensor get() {
    return statistics;
  }
  
  @Override
  public void increment(SufficientStatistics other, double multiplier) {
    Preconditions.checkArgument(other instanceof TensorSufficientStatistics);
    TensorSufficientStatistics otherStats = (TensorSufficientStatistics) other;
    statistics = statistics.elementwiseAddition(
        otherStats.statistics.elementwiseProduct(multiplier));
  }
  
  public void increment(Tensor other, double multiplier) {
    statistics = statistics.elementwiseAddition(other.elementwiseProduct(multiplier));
  }

  @Override
  public void transferParameters(SufficientStatistics other) {
    throw new UnsupportedOperationException("Not yet implemented.");
  }

  @Override
  public void increment(double amount) {
    statistics = statistics.elementwiseAddition(amount);
  }

  @Override
  public void multiply(double amount) {
    statistics = statistics.elementwiseProduct(amount);
  }

  @Override
  public void perturb(double stddev) {
    Tensor random = DenseTensor.random(statistics.getDimensionNumbers(), 
        statistics.getDimensionSizes(), 0.0, stddev);
    // May as well densify statistics, since the random tensor will be dense.
    statistics = random.elementwiseAddition(statistics);
  }

  @Override
  public void softThreshold(double threshold) {
    statistics = statistics.softThreshold(threshold);
  }

  @Override
  public SufficientStatistics duplicate() {
    return new TensorSufficientStatistics(statisticNames, statistics);
  }

  @Override
  public double innerProduct(SufficientStatistics other) {
    Preconditions.checkArgument(other instanceof TensorSufficientStatistics);
    TensorSufficientStatistics otherStats = (TensorSufficientStatistics) other;
    return statistics.innerProduct(otherStats.statistics).getByDimKey();
  }

  @Override
  public double getL2Norm() {
    return statistics.getL2Norm();
  }

  @Override
  public ListSufficientStatistics coerceToList() {
    throw new UnsupportedOperationException();
  }
}
