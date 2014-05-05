package com.jayantkrish.jklol.cvsm;

import java.io.Serializable;
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

  public CvsmSufficientStatistics(IndexedList<String> names,
      List<Supplier<SufficientStatistics>> families, List<SufficientStatistics> statistics) {
    this.names = Preconditions.checkNotNull(names);
    this.families = Preconditions.checkNotNull(families);
    this.statistics = Lists.newArrayList(Preconditions.checkNotNull(statistics));

    Preconditions.checkArgument(names.size() == families.size());
    Preconditions.checkArgument(names.size() == statistics.size());
  }

  /**
   * Gets the number of parameters in this list of parameters.
   * 
   * @return
   */
  public int size() {
    return statistics.size();
  }

  public IndexedList<String> getNames() {
    return names;
  }

  /**
   * Gets the sufficient statistics associated with index i.
   * 
   * @param i
   * @return
   */
  public SufficientStatistics getSufficientStatistics(int i) {
    if (statistics.get(i) == null) {
      // Note that the expected contract is that mutating the returned
      // value affects the values in this.
      statistics.set(i, families.get(i).get());
    }
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
    if (statistics.get(i) == null) {
      statistics.set(i, families.get(i).get());
    }
    statistics.get(i).increment(increment, 1.0);
  }

  @Override
  public void increment(SufficientStatistics other, double multiplier) {
    Preconditions.checkNotNull(other);
    Preconditions.checkArgument(other instanceof CvsmSufficientStatistics);

    List<SufficientStatistics> otherList = ((CvsmSufficientStatistics) other).statistics;
    Preconditions.checkArgument(otherList.size() == statistics.size());

    for (int i = 0; i < statistics.size(); i++) {
      if (otherList.get(i) != null) {
        if (statistics.get(i) == null) {
          statistics.set(i, otherList.get(i).duplicate());
          statistics.get(i).multiply(multiplier);
        } else {
          statistics.get(i).increment(otherList.get(i), multiplier);
        }
      }
    }
  }

  @Override
  public void increment(double amount) {
    for (int i = 0; i < statistics.size(); i++) {
      if (statistics.get(i) == null) {
        statistics.set(i, families.get(i).get());
      }
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
      if (statistics.get(i) == null) {
        statistics.set(i, families.get(i).get());
      }
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
      if (statistics != null) {
        newStatistics.add(statistic.duplicate());
      } else {
        newStatistics.add(null);
      }
    }
    return new CvsmSufficientStatistics(names, families, newStatistics);
  }

  @Override
  public double innerProduct(SufficientStatistics other) {
    Preconditions.checkNotNull(other);
    Preconditions.checkArgument(other instanceof CvsmSufficientStatistics);

    List<SufficientStatistics> otherList = ((CvsmSufficientStatistics) other).statistics;
    Preconditions.checkArgument(otherList.size() == statistics.size());

    double value = 0.0;
    for (int i = 0; i < statistics.size(); i++) {
      if (otherList.get(i) != null && statistics.get(i) != null) {
        value += statistics.get(i).innerProduct(otherList.get(i));
      }
    }
    return value;
  }

  @Override
  public double getL2Norm() {
    double l2Norm = 0.0;
    for (int i = 0; i < statistics.size(); i++) {
      if (statistics.get(i) != null) {
        l2Norm += Math.pow(statistics.get(i).getL2Norm(), 2);
      }
    }
    return Math.sqrt(l2Norm);
  }

  @Override
  public ListSufficientStatistics coerceToList() {
    // This method could actually be implemented fairly easily,
    // at the risk of allowing inefficient code.
    throw new UnsupportedOperationException("Not implemented.");
  }

  @Override
  public void makeDense() {
    for (int i = 0; i < statistics.size(); i++) {
      if (statistics.get(i) == null) {
        statistics.set(i, families.get(i).get());
      }
      statistics.get(i).makeDense();
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
