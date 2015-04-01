package com.jayantkrish.jklol.ccg.lexinduct;

import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.chart.ChartCost;
import com.jayantkrish.jklol.ccg.chart.ChartEntry;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.models.DiscreteVariable;

public class LexiconChartCost implements ChartCost {
  
  private final List<Expression2> lexiconEntries;

  public LexiconChartCost(List<Expression2> lexiconEntries) {
    this.lexiconEntries = Preconditions.checkNotNull(lexiconEntries);
  }

  @Override
  public double apply(ChartEntry entry, int spanStart, int spanEnd, DiscreteVariable syntaxType) {
    if (spanStart != spanEnd || lexiconEntries.get(spanStart) == null) {
      return 0.0;
    }

    if (lexiconEntries.get(spanStart).equals(entry.getLexiconEntry().getLogicalForm())) {
      return 0.0;
    } else {
      return Double.NEGATIVE_INFINITY;
    }
  }
}
