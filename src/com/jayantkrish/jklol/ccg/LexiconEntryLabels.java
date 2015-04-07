package com.jayantkrish.jklol.ccg;

import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;

/**
 * Labels for the correct lexicon entries used in a
 * CCG parse.
 * 
 * @author jayant
 *
 */
public class LexiconEntryLabels {
  private final int[] spanStarts;
  private final int[] spanEnds;
  private final List<Expression2> logicalForms;

  public LexiconEntryLabels(int[] spanStarts, int[] spanEnds, List<Expression2> logicalForms) {
    Preconditions.checkArgument(spanStarts.length == spanEnds.length);
    Preconditions.checkArgument(spanStarts.length == logicalForms.size());

    this.spanStarts = spanStarts;
    this.spanEnds = spanEnds;
    this.logicalForms = logicalForms;
  }

  public int[] getSpanStarts() {
    return spanStarts;
  }

  public int[] getSpanEnds() {
    return spanEnds;
  }

  public List<Expression2> getLogicalForms() {
    return logicalForms;
  }
}
