package com.jayantkrish.jklol.models.parametric;

import java.util.Iterator;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.CoercionError;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteFactor.Outcome;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.Tensor;
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

  // This flag determines whether statisticsTensor or statistics
  // contains the current parameters.
  private boolean isDense;
  private Tensor statisticsTensor;
  private TensorBuilder statistics;

  /**
   * Copy constructor.
   * 
   * @param toCopy
   */
  private TensorSufficientStatistics(TensorSufficientStatistics toCopy) {
    this.statisticNames = Preconditions.checkNotNull(toCopy.statisticNames);
    if (toCopy.isDense) {
      this.isDense = true;
      this.statistics = toCopy.statistics.getCopy();
      this.statisticsTensor = null;
    } else {
      this.isDense = false;
      this.statistics = null;
      // Tensors are immutable, so this tensor can be shared.
      this.statisticsTensor = toCopy.statisticsTensor;
    }
  }

  /**
   * Creates sufficient statistics represented by a dense tensor.
   * 
   * @param statisticNames assigns names to the entries of {@code statistics}.
   * @param statistics
   */
  public TensorSufficientStatistics(VariableNumMap statisticNames, TensorBuilder statistics) {
    Preconditions.checkArgument(statisticNames.getDiscreteVariables().size() == statisticNames.size());
    Preconditions.checkArgument(Ints.asList(statistics.getDimensionNumbers()).equals(statisticNames.getVariableNums()));

    this.statisticNames = statisticNames;
    this.statistics = statistics;

    this.statisticsTensor = null;
    this.isDense = true;
  }

  private TensorSufficientStatistics(VariableNumMap statisticNames, Tensor statistics) {
      Preconditions.checkArgument(statisticNames.getDiscreteVariables().size() == statisticNames.size());
      Preconditions.checkArgument(Ints.asList(statistics.getDimensionNumbers()).equals(statisticNames.getVariableNums()));

    this.statisticNames = statisticNames;
    this.statistics = null;

    this.statisticsTensor = statistics;
    this.isDense = false;
  }

  /**
   * Creates a sufficient statistics vector represented using a sparse tensor.
   * 
   * @param statisticNames
   * @param statistics
   * @return
   */
  public static TensorSufficientStatistics createSparse(VariableNumMap statisticNames, Tensor statistics) {
    return new TensorSufficientStatistics(statisticNames, statistics);
  }

  /**
   * Creates a sufficient statistics vector represented using a dense tensor.
   * 
   * @param statisticNames
   * @param statistics
   * @return
   */
  public static TensorSufficientStatistics createDense(VariableNumMap statisticNames, TensorBuilder statistics) {
    return new TensorSufficientStatistics(statisticNames, statistics);
  }

  /**
   * Gets the tensor in {@code this}.
   * <p>
   * For efficiency, this method does not copy the parameters, and hence the
   * values in the returned tensor may be modified if {@code this} is updated.
   * This situation should be avoided.  
   * 
   * @return
   */
  public Tensor get() {
    if (isDense) {
      return statistics.buildNoCopy();
    } else {
      return statisticsTensor;
    }
  }

  private int[] getTensorDimensions() {
    if (isDense) {
      return statistics.getDimensionNumbers();
    } else {
      return statisticsTensor.getDimensionNumbers();
    }
  }

  private int[] getTensorSizes() {
    if (isDense) {
      return statistics.getDimensionSizes();
    } else {
      return statisticsTensor.getDimensionSizes();
    }
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
    return new TableFactor(statisticNames, get());
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
    increment(otherStats.get(), multiplier);
  }

  public void increment(Tensor other, double multiplier) {
    if (isDense) {
      statistics.incrementWithMultiplier(other, multiplier);
    } else {
      statisticsTensor = statisticsTensor.elementwiseAddition(other.elementwiseProduct(multiplier));
    }
  }

  /**
   * Increments the element of that corresponds to the statistic/parameter
   * featureAssignment.
   * 
   * @param featureAssignment
   * @param amount
   */
  public void incrementFeature(Assignment featureAssignment, double amount) {
    if (isDense) {
      statistics.incrementEntry(amount, statisticNames.assignmentToIntArray(featureAssignment));
    } else {
      Tensor increment = SparseTensor.singleElement(getTensorDimensions(), getTensorSizes(),
          statisticNames.assignmentToIntArray(featureAssignment), amount);
      statisticsTensor = statisticsTensor.elementwiseAddition(increment);
    }
  }
  
  public void incrementFeatureByName(double amount, Object ... featureName) {
    Assignment assignment = statisticNames.outcomeArrayToAssignment(featureName);
    incrementFeature(assignment, amount);
  }

  /**
   * Increments the value of {@code index} by {@code amount}.
   * 
   * @param index
   * @param amount
   */
  public void incrementFeatureByIndex(double amount, int... key) {
    if (isDense) {
      statistics.incrementEntry(amount, key);
    } else {
      Tensor increment = SparseTensor.singleElement(getTensorDimensions(), getTensorSizes(),
          key, amount);
      statisticsTensor = statisticsTensor.elementwiseAddition(increment);
    }
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
    if (isDense) {
      statistics.increment(amount);
    } else {
      statisticsTensor = statisticsTensor.elementwiseAddition(amount);
    }
  }

  @Override
  public void multiply(double amount) {
    if (isDense) {
      statistics.multiply(amount);
    } else {
      statisticsTensor = statisticsTensor.elementwiseProduct(amount);
    }
  }

  @Override
  public void softThreshold(double threshold) {
    if (isDense) {
      statistics.softThreshold(threshold);
    } else {
      statisticsTensor = statisticsTensor.softThreshold(threshold);
    }
  }

  @Override
  public void perturb(double stddev) {
    if (!isDense) {
      // Make the representation dense, since the random perturbation is dense.
      statistics = DenseTensorBuilder.copyOf(statisticsTensor);
      statisticsTensor = null;
      isDense = true;
    }

    Tensor perturbation = DenseTensor.random(getTensorDimensions(), getTensorSizes(), 0.0, stddev);
    statistics.increment(perturbation);
  }

  @Override
  public TensorSufficientStatistics duplicate() {
    return new TensorSufficientStatistics(this);
  }

  @Override
  public double innerProduct(SufficientStatistics other) {
    Preconditions.checkArgument(other instanceof TensorSufficientStatistics);
    Tensor otherStatistics = ((TensorSufficientStatistics) other).get();
    if (isDense) {
      return statistics.innerProduct(otherStatistics);
    } else {
      return statisticsTensor.innerProduct(otherStatistics).getByDimKey();
    }
  }

  @Override
  public double getL2Norm() {
    if (isDense) {
      return statistics.getL2Norm();
    } else {
      return statisticsTensor.getL2Norm();
    }
  }

  @Override
  public ListSufficientStatistics coerceToList() {
    throw new CoercionError("Cannot coerce TensorSufficientStatistics instance into ListSufficientStatistics.");
  }

  @Override
  public void makeDense() {
    if (!isDense) {
      statistics = DenseTensorBuilder.copyOf(statisticsTensor);
      statisticsTensor = null;
      isDense = true;
    }
  }

  public void getFeatureNames() {
    System.out.println("**** feature names *****" + this.statisticNames.getVariables().get(0).toString());
  }

  @Override
  public String toString() {
    return getFactor().toString();
  }
}
