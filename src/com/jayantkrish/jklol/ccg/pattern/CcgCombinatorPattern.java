package com.jayantkrish.jklol.ccg.pattern;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgParse;

public class CcgCombinatorPattern implements CcgPattern {

  private final CcgPattern left;
  private final CcgPattern right;
  
  public CcgCombinatorPattern(CcgPattern left, CcgPattern right) {
    this.left = Preconditions.checkNotNull(left);
    this.right = Preconditions.checkNotNull(right);
  }

  @Override
  public List<CcgParse> match(CcgParse parse) {
    List<CcgParse> matches = Lists.newArrayList();
    if (!parse.isTerminal()) {
      List<CcgParse> leftMatches = left.match(parse.getLeft());
      List<CcgParse> rightMatches = right.match(parse.getRight());

      for (CcgParse leftMatch : leftMatches) {
        for (CcgParse rightMatch : rightMatches) {
          matches.add(CcgParse.forNonterminal(parse.getHeadedSyntacticCategory(), parse.getSemanticHeads(),
              parse.getNodeDependencies(), parse.getNodeProbability(), leftMatch, rightMatch, parse.getCombinator(),
              parse.getUnaryRule(), parse.getSpanStart(), parse.getSpanEnd()));
        }
      }
    }
    return matches;
  }
}
