package com.jayantkrish.jklol.ccg.lexinduct;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.ccg.LexiconEntryLabels;
import com.jayantkrish.jklol.ccg.chart.ChartCost;
import com.jayantkrish.jklol.ccg.chart.ChartEntry;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.models.DiscreteVariable;

public class LexiconChartCost implements ChartCost {

  private final int[] spanIndexes;
  private final List<Expression2> lexiconEntries;

  private static final int SPAN_START_OFFSET = 100000;

  public LexiconChartCost(LexiconEntryLabels lexiconEntryLabels) {
    int[] spanStarts = lexiconEntryLabels.getSpanStarts();
    int[] spanEnds = lexiconEntryLabels.getSpanEnds();
    List<Expression2> lexiconEntries = lexiconEntryLabels.getLogicalForms();

    Preconditions.checkArgument(spanStarts.length == spanEnds.length);
    Preconditions.checkArgument(spanStarts.length == lexiconEntries.size());
    this.lexiconEntries = Preconditions.checkNotNull(lexiconEntries);

    this.spanIndexes = new int[spanStarts.length]; 
    for (int i = 0; i < spanStarts.length; i++) {
      this.spanIndexes[i] = getSpanIndex(spanStarts[i], spanEnds[i]);
    }
  }

  private static int getSpanIndex(int spanStart, int spanEnd) {
    return spanStart * SPAN_START_OFFSET + spanEnd;
  }

  @Override
  public double apply(ChartEntry entry, int spanStart, int spanEnd,
      int sentenceLength, DiscreteVariable syntaxType) {
    int spanIndex = getSpanIndex(spanStart, spanEnd);
    int entryIndex = Ints.indexOf(spanIndexes, spanIndex);
    if (entryIndex < 0) {
      return 0.0;
    } else if (entry.isTerminal() &&
        lexiconEntries.get(entryIndex).equals(entry.getLexiconEntry().getLogicalForm())) {
      return 0.0;
    } else {
      return Double.NEGATIVE_INFINITY;
    }
  }
}
