package com.jayantkrish.jklol.models.parametric;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.util.IndexedList;

/**
 * Represents a collection of {@code SufficientStatistics}, typically from
 * different factors.
 * 
 * @author jayantk
 */
public class ListSufficientStatistics implements SufficientStatistics {

  private static final long serialVersionUID = -2L;

  // Names for the statistics.
  private final IndexedList<String> names;

  // Note that the statistics in the list are mutable, but
  // the size of the list cannot change.
  private final List<SufficientStatistics> statistics;

  /**
   * Creates a collection of sufficient statistics containing all of the
   * outcomes in {@code statistics}. Each statistic is given the 
   * corresponding name from {@code names}.
   * 
   * @param names
   * @param statistics
   */
  public ListSufficientStatistics(List<String> names, List<SufficientStatistics> statistics) {
    Preconditions.checkNotNull(statistics);
    Preconditions.checkArgument(names.size() == statistics.size(),
        "Wrong number of names. Got: %s", names);
    this.names = IndexedList.create(names);
    this.statistics = Lists.newArrayList(statistics);
  }

  public ListSufficientStatistics(IndexedList<String> names, List<SufficientStatistics> statistics) {
    Preconditions.checkArgument(names.size() == statistics.size());
    this.names = names;
    this.statistics = Lists.newArrayList(statistics);
  }

  /**
   * Creates a {@code ListSufficientStatistics} containing no parameters.
   * 
   * @return
   */
  public static ListSufficientStatistics empty() {
    return new ListSufficientStatistics(Lists.<String>newArrayList(),
        Lists.<SufficientStatistics>newArrayList());
  }

  /**
   * Gets the names of the statistics in {@code this}.
   *
   * @return
   */
  public IndexedList<String> getStatisticNames() {
    return names;
  }
  
  /**
   * Gets a particular set of sufficient statistics from this vector. 
   * The returned statistics are a reference to statistics in {@code this}, 
   * and therefore may be mutated to affect the value of {@code this}. 
   * Returns {@code null} if no statistics exist with {@code name}.
   *
   * @return
   */
  public SufficientStatistics getStatisticByName(String name) {
    if (names.contains(name)) {
      return statistics.get(names.getIndex(name));
    }
    return null;
  }
      
  /**
   * Returns the collection of statistics in {@code this}, in the same order
   * they were given during construction.
   * 
   * @return
   */
  public List<SufficientStatistics> getStatistics() {
    return statistics;
  }

  @Override
  public void increment(SufficientStatistics other, double multiplier) {
    Preconditions.checkNotNull(other);
    Preconditions.checkArgument(other instanceof ListSufficientStatistics);

    ListSufficientStatistics otherList = (ListSufficientStatistics) other;
    Preconditions.checkArgument(otherList.statistics.size() == statistics.size());
    
    for (int i = 0; i < statistics.size(); i++) {
      statistics.get(i).increment(otherList.statistics.get(i), multiplier);
    }
  }
  
  @Override
  public void increment(double constant) {
    for (int i = 0; i < statistics.size(); i++) {
      statistics.get(i).increment(constant);
    }
  }
  
  @Override
  public void transferParameters(SufficientStatistics other) {
    Preconditions.checkNotNull(other);
    Preconditions.checkArgument(other instanceof ListSufficientStatistics);

    ListSufficientStatistics otherList = (ListSufficientStatistics) other;
    Preconditions.checkArgument(otherList.statistics.size() == statistics.size());
    
    for (int i = 0; i < statistics.size(); i++) {
      statistics.get(i).transferParameters(otherList.statistics.get(i));
    }
  }
  
  @Override
  public void multiply(double amount) {
    for (int i = 0; i < statistics.size(); i++) {
      statistics.get(i).multiply(amount);
    }
  }
  
  @Override
  public void perturb(double stddev) {
    for (int i = 0; i < statistics.size(); i++) {
      statistics.get(i).perturb(stddev);
    }
  }
  
  @Override
  public void softThreshold(double threshold) {
    for (int i = 0; i < statistics.size(); i++) {
      statistics.get(i).softThreshold(threshold);
    }
  }
  
  @Override
  public void findEntriesLargerThan(double threshold) {
    for (int i = 0; i < statistics.size(); i++) {
      statistics.get(i).findEntriesLargerThan(threshold);
    }
  }
  
  @Override
  public ListSufficientStatistics duplicate() {
    List<SufficientStatistics> newStatistics = Lists.newArrayList();
    for (SufficientStatistics statistic : statistics) {
      newStatistics.add(statistic.duplicate());
    }
    return new ListSufficientStatistics(names.items(), newStatistics);
  }
  
  @Override
  public double innerProduct(SufficientStatistics other) {
    Preconditions.checkArgument(other instanceof ListSufficientStatistics);

    ListSufficientStatistics otherList = (ListSufficientStatistics) other;
    Preconditions.checkArgument(otherList.statistics.size() == statistics.size());
    
    double value = 0.0;
    for (int i = 0; i < statistics.size(); i++) {
      value += statistics.get(i).innerProduct(otherList.statistics.get(i));
    }
    return value;
  }

  @Override
  public double getL2Norm() {
    double norm = 0.0;
    for (int i = 0; i < statistics.size(); i++) {
      norm += Math.pow(statistics.get(i).getL2Norm(), 2);
    }
    return Math.sqrt(norm);
  }
  
  @Override
  public ListSufficientStatistics coerceToList() {
    return this;
  }
  
  @Override
  public void makeDense() {
    for (SufficientStatistics statistic : statistics) {
      statistic.makeDense();
    }
  }

  @Override
  public void zeroOut() {
    for (SufficientStatistics statistic : statistics) {
      statistic.zeroOut();
    }
  }

  @Override
  public void incrementSquare(SufficientStatistics other, double multiplier) {
    Preconditions.checkNotNull(other);
    Preconditions.checkArgument(other instanceof ListSufficientStatistics);

    ListSufficientStatistics otherList = (ListSufficientStatistics) other;
    Preconditions.checkArgument(otherList.statistics.size() == statistics.size());

    for (int i = 0; i < statistics.size(); i++) {
      statistics.get(i).incrementSquare(otherList.statistics.get(i), multiplier);
    }
  }

  @Override
  public void incrementSquareAdagrad(SufficientStatistics gradient,
      SufficientStatistics currentParameters, double regularization) {
    Preconditions.checkNotNull(gradient);
    Preconditions.checkArgument(gradient instanceof ListSufficientStatistics);
    Preconditions.checkNotNull(currentParameters);
    Preconditions.checkArgument(currentParameters instanceof ListSufficientStatistics);

    ListSufficientStatistics gradientList = (ListSufficientStatistics) gradient;
    Preconditions.checkArgument(gradientList.statistics.size() == statistics.size());
    ListSufficientStatistics parametersList = (ListSufficientStatistics) currentParameters;
    Preconditions.checkArgument(parametersList.statistics.size() == statistics.size());
    
    for (int i = 0; i < statistics.size(); i++) {
      statistics.get(i).incrementSquareAdagrad(gradientList.statistics.get(i),
          parametersList.statistics.get(i), regularization);
    }
  }

  @Override
  public void multiplyInverseAdagrad(SufficientStatistics other, double constant,
      double multiplier) {
    Preconditions.checkNotNull(other);
    Preconditions.checkArgument(other instanceof ListSufficientStatistics);

    ListSufficientStatistics otherList = (ListSufficientStatistics) other;
    Preconditions.checkArgument(otherList.statistics.size() == statistics.size());

    for (int i = 0; i < statistics.size(); i++) {
      statistics.get(i).multiplyInverseAdagrad(otherList.statistics.get(i),
          constant, multiplier);
    }
  }

  @Override
  public void incrementAdagrad(SufficientStatistics gradient, SufficientStatistics sumSquares,
      double multiplier) {
    Preconditions.checkNotNull(gradient);
    Preconditions.checkArgument(gradient instanceof ListSufficientStatistics);
    Preconditions.checkNotNull(sumSquares);
    Preconditions.checkArgument(sumSquares instanceof ListSufficientStatistics);

    ListSufficientStatistics gradientList = (ListSufficientStatistics) gradient;
    Preconditions.checkArgument(gradientList.statistics.size() == statistics.size());
    ListSufficientStatistics squareList = (ListSufficientStatistics) sumSquares;
    Preconditions.checkArgument(squareList.statistics.size() == statistics.size());

    for (int i = 0; i < statistics.size(); i++) {
      statistics.get(i).incrementAdagrad(gradientList.statistics.get(i),
          squareList.statistics.get(i), multiplier);
    }
  }

  @Override
  public String getDescription() {
    StringBuilder sb = new StringBuilder();
    for (SufficientStatistics statistic : statistics) {
      sb.append(statistic.getDescription());
      sb.append("\n");
    }
    return sb.toString();
  }

  @Override
  public String toString() {
    return statistics.toString();
  }
}
