package com.jayantkrish.jklol.ccg.pattern;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgParse;

public class CcgRecursivePattern implements CcgPattern {
  
  private final CcgPattern pattern;

  public CcgRecursivePattern(CcgPattern pattern) {
    this.pattern = Preconditions.checkNotNull(pattern);
  }

  @Override
  public List<CcgParse> match(CcgParse parse) {
    return Arrays.asList(replaceHelper(parse));
  }

  private final CcgParse replaceHelper(CcgParse parse) {
    CcgParse replacedParse = parse;
    if (!parse.isTerminal()) {
      CcgParse left = replaceHelper(parse.getLeft());
      CcgParse right = replaceHelper(parse.getRight());

      replacedParse = CcgParse.forNonterminal(parse.getHeadedSyntacticCategory(),
          parse.getSemanticHeads(), parse.getNodeDependencies(), parse.getNodeProbability(),
          left, right, parse.getCombinator(), parse.getUnaryRule(), parse.getSpanStart(),
          parse.getSpanEnd());
    }
    List<CcgParse> replacements = pattern.match(replacedParse);
    Preconditions.checkArgument(replacements.size() == 1);
    return replacements.get(0);
  }
}

