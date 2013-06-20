package com.jayantkrish.jklol.ccg.supertag;

import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgChart;
import com.jayantkrish.jklol.ccg.CcgChart.ChartEntry;
import com.jayantkrish.jklol.ccg.CcgChart.ChartFilter;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.SyntacticCategory;
import com.jayantkrish.jklol.ccg.SyntacticChartFilter.SyntacticCompatibilityFunction;
import com.jayantkrish.jklol.models.DiscreteVariable;

/**
 * Chart filter for integrating a supertagger with CCG parsing. This
 * filter eliminates terminal entries whose syntactic category does not
 * agree with a prespecified list of syntactic categories.
 *  
 * @author jayantk
 */
public class SupertagChartFilter implements ChartFilter {
  
  private final List<List<SyntacticCategory>> supertags;
  private final SyntacticCompatibilityFunction compatibilityFunction;

  public SupertagChartFilter(List<List<SyntacticCategory>> supertags,
      SyntacticCompatibilityFunction compatibilityFunction) {
    this.supertags = Preconditions.checkNotNull(supertags);
    this.compatibilityFunction = Preconditions.checkNotNull(compatibilityFunction);
  }

  @Override
  public boolean apply(ChartEntry entry, int spanStart, int spanEnd, DiscreteVariable syntaxType) {
    // This filter only applies to single word terminal entries.
    if (spanStart != spanEnd) {
      return true;
    } 

    HeadedSyntacticCategory entrySyntax = (HeadedSyntacticCategory) syntaxType
        .getValue(entry.getHeadedSyntax());
    for (SyntacticCategory supertag : supertags.get(spanStart)) {
      if (compatibilityFunction.apply(supertag, entrySyntax)) {
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
