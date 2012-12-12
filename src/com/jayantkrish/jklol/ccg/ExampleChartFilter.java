package com.jayantkrish.jklol.ccg;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgChart.ChartEntry;
import com.jayantkrish.jklol.ccg.CcgChart.ChartFilter;

public class ExampleChartFilter implements ChartFilter {

  private final CcgExample example;
  
  public ExampleChartFilter(CcgExample example) {
    this.example = Preconditions.checkNotNull(example);
  }
  
  @Override
  public boolean apply(ChartEntry entry, int spanStart, int spanEnd) {
    // TODO
    return true;
  }
}
