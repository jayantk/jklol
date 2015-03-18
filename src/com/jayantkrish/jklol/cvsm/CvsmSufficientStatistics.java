package com.jayantkrish.jklol.cvsm;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.parametric.ListSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.ParametricFamily;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.util.IndexedList;

/**
 * Sufficient statistics for compositional vector space models. This
 * class is essentially a lazy version of
 * {@code ListSufficientStatistics}, where blocks of this parameter
 * vector are only initialized if they are nonzero.
 * 
 * TODO: refactor into core jklol?
 * 
 * @author jayantk
 */
public class CvsmSufficientStatistics implements SufficientStatistics {

  private static final long serialVersionUID = 1L;

  // Names for the statistics.
  private final IndexedList<String> names;
  private final List<Supplier<SufficientStatistics>> families;
  private final List<SufficientStatistics> statistics;

  // Indexes of non-null (= zero) elements of statistics
  private final int[] nonzeroIndexes;
  private int numNonzeroIndexes;

  public CvsmSufficientStatistics(IndexedList<String> names,
      List<Supplier<SufficientStatistics>> families, List<SufficientStatistics> statistics,
      int[] nonzeroIndexes, int numNonzeroIndexes) {
    this.names = Preconditions.checkNotNull(names);
    this.families = Preconditions.checkNotNull(families);
    this.statistics = Preconditions.checkNotNull(statistics);

    this.nonzeroIndexes = Preconditions.checkNotNull(nonzeroIndexes);
    this.numNonzeroIndexes= numNonzeroIndexes;

    Preconditions.checkArgument(names.size() == families.size());
    Preconditions.checkArgument(names.size() == statistics.size());
    Preconditions.checkArgument(names.size() == nonzeroIndexes.length);
  }

  public static CvsmSufficientStatistics zero(IndexedList<String> names,
      List<Supplier<SufficientStatistics>> families) {
    int numFamilies = families.size();
    List<SufficientStatistics> parameters = Lists.newArrayList(Collections
        .<SufficientStatistics>nCopies(numFamilies, null));
    return new CvsmSufficientStatistics(names, families, parameters, 
        new int[numFamilies], 0);
  }

  /**
   * Gets the number of parameters in this list of parameters.
   * 
   * @return
   */
  public int size() {
    return families.size();
  }

  public IndexedList<String> getNames() {
    return names;
  }

  private final void ensureStatisticInstantiated(int index) {
    if (statistics.get(index) == null) {
      statistics.set(index, families.get(index).get());

      if (numNonzeroIndexes >= nonzeroIndexes.length) {
        Arrays.sort(nonzeroIndexes);
        System.out.println(Arrays.toString(nonzeroIndexes));
      }

      nonzeroIndexes[numNonzeroIndexes] = index;
      numNonzeroIndexes++;
    }
  }

  /**
   * Gets the sufficient statistics associated with index i.
   * 
   * @param i
   * @return
   */
  public SufficientStatistics getSufficientStatistics(int i) {
    // Note that the expected contract is that mutating the returned
    // value affects the values in this.
    ensureStatisticInstantiated(i);
    return statistics.get(i);
  }

  /**
   * Adds {@code increment} to the {@code i}th parameter vector in
   * this.
   * 
   * @param i
   * @param increment
   */
  public void incrementEntry(int i, SufficientStatistics increment) {
    ensureStatisticInstantiated(i);
    statistics.get(i).increment(increment, 1.0);
  }

  @Override
  public void increment(SufficientStatistics other, double multiplier) {
    Preconditions.checkNotNull(other);
    Preconditions.checkArgument(other instanceof CvsmSufficientStatistics);

    CvsmSufficientStatistics otherStats = ((CvsmSufficientStatistics) other);
    List<SufficientStatistics> otherList = otherStats.statistics;
    int[] otherNonZeroInds = otherStats.nonzeroIndexes;
    int otherNumNonZero = otherStats.numNonzeroIndexes;
    Preconditions.checkArgument(otherList.size() == statistics.size());

    for (int i = 0; i < otherNumNonZero; i++) {
      int ind = otherNonZeroInds[i];
      Preconditions.checkState(otherList.get(ind) != null);
      ensureStatisticInstantiated(ind);
      statistics.get(ind).increment(otherList.get(ind), multiplier);
    }
  }

  @Override
  public void increment(double amount) {
    for (int i = 0; i < statistics.size(); i++) {
      ensureStatisticInstantiated(i);
      statistics.get(i).increment(amount);
    }
  }

  @Override
  public void transferParameters(SufficientStatistics other) {
    throw new UnsupportedOperationException("not implemented.");
  }

  @Override
  public void multiply(double amount) {
    for (int i = 0; i < statistics.size(); i++) {
      if (statistics.get(i) != null) {
        statistics.get(i).multiply(amount);
      }
    }
  }

  @Override
  public void perturb(double stddev) {
    for (int i = 0; i < statistics.size(); i++) {
      ensureStatisticInstantiated(i);
      statistics.get(i).perturb(stddev);
    }
  }

  @Override
  public void softThreshold(double threshold) {
    for (int i = 0; i < statistics.size(); i++) {
      if (statistics.get(i) != null) {
        statistics.get(i).softThreshold(threshold);
      }
    }
  }

  @Override
  public void findEntriesLargerThan(double threshold) {
    for (int i = 0; i < statistics.size(); i++) {
      if (statistics.get(i) != null) {
        statistics.get(i).findEntriesLargerThan(threshold);
      }
    }
  }

  @Override
  public SufficientStatistics duplicate() {
    List<SufficientStatistics> newStatistics = Lists.newArrayList();
    for (SufficientStatistics statistic : statistics) {
      if (statistic != null) {
        newStatistics.add(statistic.duplicate());
      } else {
        newStatistics.add(null);
      }
    }
    return new CvsmSufficientStatistics(names, families, newStatistics,
        Arrays.copyOf(nonzeroIndexes, nonzeroIndexes.length), numNonzeroIndexes);
  }

  @Override
  public double innerProduct(SufficientStatistics other) {
    Preconditions.checkNotNull(other);
    Preconditions.checkArgument(other instanceof CvsmSufficientStatistics);

    CvsmSufficientStatistics otherStats = ((CvsmSufficientStatistics) other);
    List<SufficientStatistics> otherList = otherStats.statistics;
    int[] otherNonZeroInds = otherStats.nonzeroIndexes;
    int otherNumNonZero = otherStats.numNonzeroIndexes;
    Preconditions.checkArgument(otherList.size() == statistics.size());

    double value = 0.0;
    for (int i = 0; i < otherNumNonZero; i++) {
      int ind = otherNonZeroInds[i];
      if (otherList.get(ind) != null && statistics.get(ind) != null) {
        value += statistics.get(ind).innerProduct(otherList.get(ind));
      }
    }
    return value;
  }

  @Override
  public double getL2Norm() {
    double l2Norm = 0.0;
    for (int i = 0; i < numNonzeroIndexes; i++) {
      int ind = nonzeroIndexes[i];
      if (statistics.get(ind) != null) {
        l2Norm += Math.pow(statistics.get(ind).getL2Norm(), 2);
      }
    }
    return Math.sqrt(l2Norm);
  }

  @Override
  public ListSufficientStatistics coerceToList() {
    for (int i = 0; i < statistics.size(); i++) {
      ensureStatisticInstantiated(i);
    }
    return new ListSufficientStatistics(names, statistics);
  }

  @Override
  public void makeDense() {
    for (int i = 0; i < statistics.size(); i++) {
      ensureStatisticInstantiated(i);
      statistics.get(i).makeDense();
    }
  }

  @Override 
  public void zeroOut() {
    int numDeleted = 0;
    for (int i = 0; i < numNonzeroIndexes; i++) {
      int ind = nonzeroIndexes[i];
      if (statistics.get(ind) instanceof CvsmSufficientStatistics) {
        statistics.get(ind).zeroOut();
        nonzeroIndexes[i - numDeleted] = ind;
      } else {
        statistics.set(ind, null);
        nonzeroIndexes[i] = -1;
        numDeleted++;
      }
    }
    numNonzeroIndexes = numNonzeroIndexes - numDeleted;
  }

  @Override
  public void incrementSquare(SufficientStatistics other, double multiplier) {
    Preconditions.checkNotNull(other);
    Preconditions.checkArgument(other instanceof CvsmSufficientStatistics);

    CvsmSufficientStatistics otherStats = ((CvsmSufficientStatistics) other);
    List<SufficientStatistics> otherList = otherStats.statistics;
    int[] otherNonZeroInds = otherStats.nonzeroIndexes;
    int otherNumNonZero = otherStats.numNonzeroIndexes;
    Preconditions.checkArgument(otherList.size() == statistics.size());

    for (int i = 0; i < otherNumNonZero; i++) {
      int ind = otherNonZeroInds[i];
      Preconditions.checkState(otherList.get(ind) != null);
      ensureStatisticInstantiated(ind);
      statistics.get(ind).incrementSquare(otherList.get(ind), multiplier);
    }
  }

  @Override
  public void incrementSquareAdagrad(SufficientStatistics gradient,
      SufficientStatistics currentParameters, double regularization) {
    Preconditions.checkNotNull(gradient);
    Preconditions.checkArgument(gradient instanceof CvsmSufficientStatistics);
    Preconditions.checkNotNull(currentParameters);
    Preconditions.checkArgument(currentParameters instanceof CvsmSufficientStatistics);
    
    CvsmSufficientStatistics gradientStats = ((CvsmSufficientStatistics) gradient);
    List<SufficientStatistics> gradientList = gradientStats.statistics;
    CvsmSufficientStatistics parameterStats = ((CvsmSufficientStatistics) currentParameters);
    List<SufficientStatistics> parameterList = parameterStats.statistics;

    for (int i = 0; i < statistics.size(); i++) {
      SufficientStatistics gradientStat = gradientList.get(i);
      SufficientStatistics parameterStat = parameterList.get(i);
      
      if (gradientStat != null && parameterStat != null) {
        ensureStatisticInstantiated(i);
        statistics.get(i).incrementSquareAdagrad(gradientStat, parameterStat, regularization);
      } else if (gradientStat != null) {
        ensureStatisticInstantiated(i);
        statistics.get(i).incrementSquare(gradientStat, 1.0);
      } else if (parameterStat != null) {
        ensureStatisticInstantiated(i);
        statistics.get(i).incrementSquare(parameterStat, regularization);
      }
    }    
  }

  @Override
  public void multiplyInverseAdagrad(SufficientStatistics sumSquares, double constant,
      double multiplier) {
    Preconditions.checkNotNull(sumSquares);
    Preconditions.checkArgument(sumSquares instanceof CvsmSufficientStatistics);

    CvsmSufficientStatistics otherStats = ((CvsmSufficientStatistics) sumSquares);
    List<SufficientStatistics> otherList = otherStats.statistics;
    Preconditions.checkArgument(otherList.size() == statistics.size());

    for (int i = 0; i < numNonzeroIndexes; i++) {
      int ind = nonzeroIndexes[i];
      // The corresponding parameters in other cannot be null, because 
      // then this operation is performing division by zero.
      Preconditions.checkState(otherList.get(ind) != null);
      ensureStatisticInstantiated(ind);
      statistics.get(ind).multiplyInverseAdagrad(otherList.get(ind), constant, multiplier);
    }
  }

  @Override
  public void incrementAdagrad(SufficientStatistics gradient, SufficientStatistics sumSquares,
      double multiplier) {
    Preconditions.checkNotNull(gradient);
    Preconditions.checkArgument(gradient instanceof CvsmSufficientStatistics);

    CvsmSufficientStatistics gradientStats = ((CvsmSufficientStatistics) gradient);
    List<SufficientStatistics> gradientList = gradientStats.statistics;
    int[] gradientNonZeroInds = gradientStats.nonzeroIndexes;
    int gradientNumNonZero = gradientStats.numNonzeroIndexes;
    Preconditions.checkArgument(gradientList.size() == statistics.size());
    
    CvsmSufficientStatistics sumSquareStats = ((CvsmSufficientStatistics) sumSquares);
    List<SufficientStatistics> sumSquareList = sumSquareStats.statistics;
    Preconditions.checkArgument(sumSquareList.size() == statistics.size());

    for (int i = 0; i < gradientNumNonZero; i++) {
      int ind = gradientNonZeroInds[i];
      Preconditions.checkState(gradientList.get(ind) != null);
      Preconditions.checkState(sumSquareList.get(ind) != null);
      ensureStatisticInstantiated(ind);
      statistics.get(ind).incrementAdagrad(gradientList.get(ind), sumSquareList.get(ind),
          multiplier);
    }
  }

  @Override
  public String getDescription() {
    return statistics.toString();
  }

  public static class ParametricFamilySupplier implements Supplier<SufficientStatistics>, Serializable {
    private static final long serialVersionUID = 1L;

    private final ParametricFamily<?> family;

    public ParametricFamilySupplier(ParametricFamily<?> family) {
      this.family = Preconditions.checkNotNull(family);
    }

    @Override
    public SufficientStatistics get() {
      return family.getNewSufficientStatistics();
    }
  }
}
