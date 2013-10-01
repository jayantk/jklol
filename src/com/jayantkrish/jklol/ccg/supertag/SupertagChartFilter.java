package com.jayantkrish.jklol.ccg.supertag;

import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.chart.CcgChart;
import com.jayantkrish.jklol.ccg.chart.ChartEntry;
import com.jayantkrish.jklol.ccg.chart.ChartFilter;
import com.jayantkrish.jklol.models.DiscreteVariable;

/**
 * Chart filter for integrating a supertagger with CCG parsing. This
 * filter eliminates terminal entries whose syntactic category is not
 * in a prespecified list of syntactic categories.
 *  
 * @author jayantk
 */
public class SupertagChartFilter implements ChartFilter {
  
  private final List<List<HeadedSyntacticCategory>> supertags;

  public SupertagChartFilter(List<List<HeadedSyntacticCategory>> supertags) {
    this.supertags = Preconditions.checkNotNull(supertags);
  }

  @Override
  public boolean apply(ChartEntry entry, int spanStart, int spanEnd, DiscreteVariable syntaxType) {
    // This filter only applies to single word terminal entries where
    // the example has a specified set of valid supertags.
    if (spanStart != spanEnd || supertags.get(spanStart).size() == 0) {
      return true;
    } 

    HeadedSyntacticCategory entrySyntax = (HeadedSyntacticCategory) syntaxType
        .getValue(entry.getHeadedSyntax());
    for (HeadedSyntacticCategory supertag : supertags.get(spanStart)) {
      if (entrySyntax.equals(supertag)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void applyToTerminals(CcgChart chart) {
    // No need to modify chart -- all of the work is done in apply.
  }
}
