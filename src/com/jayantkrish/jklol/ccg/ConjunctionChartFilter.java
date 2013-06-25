package com.jayantkrish.jklol.ccg;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgChart.ChartEntry;
import com.jayantkrish.jklol.ccg.CcgChart.ChartFilter;
import com.jayantkrish.jklol.models.DiscreteVariable;

/**
 * Combines several chart filters using a logical and.
 * 
 * @author jayantk
 */
public class ConjunctionChartFilter implements ChartFilter {
  
  private final List<ChartFilter> filters;

  public ConjunctionChartFilter(List<ChartFilter> filters) {
    this.filters = ImmutableList.copyOf(filters);
  }

  /**
   * Creates a chart filter ignoring any {@code null} filters, 
   * which are interpreted as the always-true filter. Returns
   * {@code null} if all of the given filters are {@code null}.
   * 
   * @param filters
   * @return
   */
  public static ConjunctionChartFilter create(ChartFilter ... filters) {
    List<ChartFilter> nonNullFilters = Lists.newArrayList();
    for (ChartFilter filter : filters) {
      if (filter != null) {
        nonNullFilters.add(filter);
      }
    }
    
    if (nonNullFilters.size() > 0) {
      return new ConjunctionChartFilter(nonNullFilters);
    } else {
      return null;
    }
  }

  @Override
  public boolean apply(ChartEntry entry, int spanStart, int spanEnd, DiscreteVariable syntaxVarType) {
    for (ChartFilter filter : filters) {
      if (!filter.apply(entry, spanStart, spanEnd, syntaxVarType)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void applyToTerminals(CcgChart chart) {
    for (ChartFilter filter : filters) {
      filter.applyToTerminals(chart);
    }
  }
}
