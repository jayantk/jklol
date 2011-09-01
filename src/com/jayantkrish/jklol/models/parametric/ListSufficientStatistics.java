package com.jayantkrish.jklol.models.parametric;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.jayantkrish.jklol.models.CoercionError;
import com.jayantkrish.jklol.models.bayesnet.Cpt;
import com.jayantkrish.jklol.models.loglinear.FeatureSufficientStatistics;

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
  public Cpt coerceToCpt() {
    throw new CoercionError("Cannot coerce ListSufficientStatistics to Cpt.");
  }
  
  @Override
  public FeatureSufficientStatistics coerceToFeature() {
    throw new CoercionError("Cannot coerce ListSufficientStatistics instance into a FeatureSufficientStatistics.");
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
