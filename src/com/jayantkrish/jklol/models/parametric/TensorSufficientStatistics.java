package com.jayantkrish.jklol.models.parametric;

import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.CoercionError;
import com.jayantkrish.jklol.tensor.TensorBuilder;

/**
 * Stores sufficient statistics in one or more tensors. This implementation of
 * {@link SufficientStatistic} should be sufficient for most
 * {@link ParametricFactor}s.
 * 
 * @author jayantk
 */
public class TensorSufficientStatistics implements SufficientStatistics {

  private final List<TensorBuilder> statistics;

  public TensorSufficientStatistics(List<TensorBuilder> statistics) {
    this.statistics = statistics;
  }

  /**
   * Returns the number of tensors stored in {@code this}.
   * 
   * @return
   */
  public int size() {
    return statistics.size();
  }

  /**
   * Gets the {@code i}th tensor in {@code this}. Requires
   * {@code 0 <= i < this.size()}.
   * 
   * @param i
   * @return
   */
  public TensorBuilder get(int i) {
    return statistics.get(i);
  }

  @Override
  public void increment(SufficientStatistics other, double multiplier) {
    Preconditions.checkArgument(other instanceof TensorSufficientStatistics);
    TensorSufficientStatistics otherStats = (TensorSufficientStatistics) other;
    Preconditions.checkArgument(otherStats.statistics.size() == statistics.size());
    for (int i = 0; i < statistics.size(); i++) {
      statistics.get(i).incrementWithMultiplier(otherStats.statistics.get(i), multiplier);
    }
  }

  @Override
  public void increment(double amount) {
    for (int i = 0; i < statistics.size(); i++) {
      statistics.get(i).increment(amount);
    }
  }

  @Override
  public void multiply(double amount) {
    for (int i = 0; i < statistics.size(); i++) {
      statistics.get(i).multiply(amount);
    }
  }

  @Override
  public double getL2Norm() {
    double sumSquares = 0.0;
    for (int i = 0; i < statistics.size(); i++) {
      double norm = statistics.get(i).getL2Norm();
      sumSquares += norm * norm;
    }
    return Math.sqrt(sumSquares);
  }

  @Override
  public ListSufficientStatistics coerceToList() {
    throw new CoercionError("Cannot coerce TensorSufficientStatistics instance into ListSufficientStatistics.");
  }
  
  @Override
  public String toString() {
    return statistics.toString();
  }
}
