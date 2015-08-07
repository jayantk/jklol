package com.jayantkrish.jklol.ccg.chart;

import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis;
import com.jayantkrish.jklol.models.DiscreteVariable;

/**
 * A chart cost derived from a logical form. Prevents chart
 * entries from containing predicates that are not contained
 * in the logical form.
 * 
 * @author jayantk
 *
 */
public class PredicateChartCost implements ChartCost {

  private final Expression2 logicalForm;
  private final Set<String> predicates;
  
  public PredicateChartCost(Expression2 logicalForm) {
    this.logicalForm = Preconditions.checkNotNull(logicalForm);
    this.predicates = Sets.newHashSet(StaticAnalysis.getFreeVariables(logicalForm));
  }

  @Override
  public double apply(ChartEntry entry, int spanStart, int spanEnd, int sentenceLength,
      DiscreteVariable syntaxVarType) {
    if (entry.isTerminal()) {
      Expression2 entryLf = entry.getLexiconEntry().getLogicalForm();
      if (!predicates.containsAll(StaticAnalysis.getFreeVariables(entryLf))) {
        return Double.NEGATIVE_INFINITY;
      }
    }

    return 0.0;
  }
}
