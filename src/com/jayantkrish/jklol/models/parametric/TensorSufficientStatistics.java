package com.jayantkrish.jklol.models.parametric;

import java.util.Iterator;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.CoercionError;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteFactor.Outcome;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.tensor.TensorBuilder;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Stores sufficient statistics in a tensors. This implementation of
 * {@link SufficientStatistic} should be sufficient for most
 * {@link ParametricFactor}s.
 * 
 * @author jayantk
 */
public class TensorSufficientStatistics implements SufficientStatistics {

  private static final long serialVersionUID = -888818836179147365L;

  private final VariableNumMap statisticNames;
  private final TensorBuilder statistics;

  /**
   * 
   * @param statisticNames assigns names to the entries of {@code statistics}.
   * @param statistics
   */
  public TensorSufficientStatistics(VariableNumMap statisticNames,
      TensorBuilder statistics) {
    Preconditions.checkArgument(statisticNames.getDiscreteVariables().size() == statisticNames.size());
    Preconditions.checkArgument(Ints.asList(statistics.getDimensionNumbers()).equals(statisticNames.getVariableNums()));

    this.statisticNames = statisticNames;
    this.statistics = statistics;
  }

  /**
   * Gets the tensor in {@code this}.
   * 
   * @return
   */
  public TensorBuilder get() {
    return statistics;
  }

  /**
   * Gets the tensor in {@code this}, wrapped in a factor that provides names
   * for the statistics/parameters. The returned factor provides an immutable
   * snapshot of the current values of the parameters, and will not reflect
   * future modifications.
   * 
   * @return
   */
  public DiscreteFactor getFactor() {
    return new TableFactor(statisticNames, statistics.build());
  }

  /**
   * Gets the mapping from statistic/parameter names to tensor indexes.
   * 
   * @return
   */
  public VariableNumMap getStatisticNames() {
    return statisticNames;
  }

  @Override
  public void increment(SufficientStatistics other, double multiplier) {
    Preconditions.checkArgument(other instanceof TensorSufficientStatistics);
    TensorSufficientStatistics otherStats = (TensorSufficientStatistics) other;
    statistics.incrementWithMultiplier(otherStats.statistics, multiplier);
  }

  /**
   * Increments the element of that corresponds to the statistic/parameter
   * featureAssignment.
   * 
   * @param featureAssignment
   * @param amount
   */
  public void incrementFeature(Assignment featureAssignment, double amount) {
    statistics.incrementEntry(amount, statisticNames.assignmentToIntArray(featureAssignment));
  }

  @Override
  public void transferParameters(SufficientStatistics other) {
    DiscreteFactor otherFactor = ((TensorSufficientStatistics) other).getFactor();

    Iterator<Outcome> outcomeIter = otherFactor.outcomeIterator();
    while (outcomeIter.hasNext()) {
      Outcome outcome = outcomeIter.next();

      Assignment assignment = outcome.getAssignment();
      if (statisticNames.isValidAssignment(assignment)) {
        incrementFeature(assignment, outcome.getProbability());
      }
    }
  }

  @Override
  public void increment(double amount) {
    statistics.increment(amount);
  }

  @Override
  public void multiply(double amount) {
    statistics.multiply(amount);
  }
  
  @Override
  public void softThreshold(double threshold) {
    statistics.softThreshold(threshold);
  }

  @Override
  public void perturb(double stddev) {
    statistics.increment(DenseTensor.random(
        statistics.getDimensionNumbers(), statistics.getDimensionSizes(), 0.0, stddev));
  }

  @Override
  public TensorSufficientStatistics duplicate() {
    return new TensorSufficientStatistics(statisticNames, statistics.getCopy());
  }

  @Override
  public double innerProduct(SufficientStatistics other) {
    Preconditions.checkArgument(other instanceof TensorSufficientStatistics);
    TensorBuilder otherStatistics = ((TensorSufficientStatistics) other).statistics;
    return statistics.innerProduct(otherStatistics);
  }

  @Override
  public double getL2Norm() {
    return statistics.getL2Norm();
  }

  @Override
  public ListSufficientStatistics coerceToList() {
    throw new CoercionError("Cannot coerce TensorSufficientStatistics instance into ListSufficientStatistics.");
  }
  
  public void getFeatureNames() {
    System.out.println("**** feature names *****" + this.statisticNames.getVariables().get(0).toString());
  }

  @Override
  public String toString() {
    return getFactor().toString();
  }
}
