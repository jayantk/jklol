package com.jayantkrish.jklol.models.parametric;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/**
 * Represents a collection of {@code SufficientStatistics}, typically from
 * different factors.
 * 
 * @author jayantk
 */
public class ListSufficientStatistics implements SufficientStatistics {

  // Note that the statistics in the list are mutable, but elements
  // cannot be added or removed from the list.
  private final ImmutableList<SufficientStatistics> statistics;

  /**
   * Creates a collection of sufficient statistics containing all of the
   * outcomes in {@code statistics}.
   * 
   * @param statistics
   */
  public ListSufficientStatistics(List<SufficientStatistics> statistics) {
    Preconditions.checkNotNull(statistics);
    this.statistics = ImmutableList.copyOf(statistics);
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
  public String toString() {
    return statistics.toString();
  }
}
