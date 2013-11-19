package com.jayantkrish.jklol.ccg.chart;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.DiscreteVariable;

/**
 * Adds together several chart costs.
 * 
 * @author jayantk
 */
public class SumChartCost implements ChartCost {
  
  private final List<ChartCost> filters;

  public SumChartCost(List<ChartCost> filters) {
    this.filters = ImmutableList.copyOf(filters);
  }

  /**
   * Creates a chart cost ignoring any {@code null} costs, 
   * which are interpreted as the always-0.0 cost. Returns
   * {@code null} if all of the given filters are {@code null}.
   * 
   * @param filters
   * @return
   */
  public static SumChartCost create(ChartCost ... filters) {
    List<ChartCost> nonNullFilters = Lists.newArrayList();
    for (ChartCost filter : filters) {
      if (filter != null) {
        nonNullFilters.add(filter);
      }
    }
    
    if (nonNullFilters.size() > 0) {
      return new SumChartCost(nonNullFilters);
    } else {
      return null;
    }
  }

  @Override
  public double apply(ChartEntry entry, int spanStart, int spanEnd, DiscreteVariable syntaxVarType) {
    double value = 0.0;
    for (ChartCost filter : filters) {
      value += filter.apply(entry, spanStart, spanEnd, syntaxVarType);
    }
    return value;
  }

  @Override
  public void applyToTerminals(CcgChart chart) {
    for (ChartCost filter : filters) {
      filter.applyToTerminals(chart);
    }
  }
}
