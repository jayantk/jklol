package com.jayantkrish.jklol.ccg;

import java.util.Map;

import com.google.common.collect.Maps;
import com.jayantkrish.jklol.ccg.CcgChart.ChartEntry;
import com.jayantkrish.jklol.ccg.CcgChart.ChartFilter;

/**
 * Filters chart entries to agree with a given syntactic tree. This
 * filter effectively conditions on the given syntactic structure, and
 * restricts the CCG parsing beam search to those parses which respect
 * the given syntax.
 * 
 * @author jayantk
 */
public class SyntacticChartFilter implements ChartFilter {

  private final Map<Integer, SyntacticCategory> binaryRuleResult;
  private final Map<Integer, SyntacticCategory> unaryRuleResult;

  private static final int SPAN_START_OFFSET = 100000;

  public SyntacticChartFilter(CcgSyntaxTree syntacticParse) {
    this.binaryRuleResult = Maps.newHashMap();
    this.unaryRuleResult = Maps.newHashMap();

    populateRuleMaps(syntacticParse);
  }

  private final void populateRuleMaps(CcgSyntaxTree parse) {
    int mapIndex = (parse.getSpanStart() * SPAN_START_OFFSET) + parse.getSpanEnd();

    binaryRuleResult.put(mapIndex, parse.getPreUnaryRuleSyntax());
    unaryRuleResult.put(mapIndex, parse.getRootSyntax());

    if (!parse.isTerminal()) {
      populateRuleMaps(parse.getLeft());
      populateRuleMaps(parse.getRight());
    }
  }

  @Override
  public boolean apply(ChartEntry entry, int spanStart, int spanEnd) {
    int mapIndex = (spanStart * SPAN_START_OFFSET) + spanEnd;

    if (!binaryRuleResult.containsKey(mapIndex)) {
      return false;
    }

    SyntacticCategory expectedRootSyntax = unaryRuleResult.get(mapIndex);
    if (!entry.getSyntax().equals(expectedRootSyntax)) {
      return false;
    }

    SyntacticCategory expectedPreUnarySyntax = binaryRuleResult.get(mapIndex);
    if (!expectedRootSyntax.equals(expectedPreUnarySyntax)) {
      return entry.getUnaryRule() != null &&
          entry.getUnaryRule().getInputSyntacticCategory().getSyntax().equals(expectedPreUnarySyntax);
    } else {
      return entry.getUnaryRule() == null;
    }
  }
}
