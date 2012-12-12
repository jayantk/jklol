package com.jayantkrish.jklol.ccg;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgChart.ChartEntry;
import com.jayantkrish.jklol.ccg.CcgChart.ChartFilter;

public class ExampleChartFilter implements ChartFilter {

  private final CcgExample example;
  
  /*
  private final Map<Integer, SyntacticCategory> binaryRuleResult;
  private final Map<Integer, SyntacticCategory> unaryRuleResult;
  */
  
  private static final int LEFT_SPAN_OFFSET = 100000;
  
  public ExampleChartFilter(CcgExample example) {
    this.example = Preconditions.checkNotNull(example);
  }
  
  @Override
  public boolean apply(ChartEntry entry, int spanStart, int spanEnd) {
    // TODO
    return true;
  }
}
