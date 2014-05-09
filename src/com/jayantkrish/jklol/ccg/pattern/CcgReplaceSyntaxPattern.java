package com.jayantkrish.jklol.ccg.pattern;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;

public class CcgReplaceSyntaxPattern implements CcgPattern {

  private final HeadedSyntacticCategory newSyntax;

  public CcgReplaceSyntaxPattern(HeadedSyntacticCategory newSyntax) {
    this.newSyntax = Preconditions.checkNotNull(newSyntax);
  }

  @Override
  public List<CcgParse> match(CcgParse parse) {
    if (parse.isTerminal()) {
      return Arrays.asList(CcgParse.forTerminal(newSyntax, parse.getLexiconEntry(), parse.getLexiconTriggerWords(),
          parse.getPosTags(), parse.getSemanticHeads(), parse.getNodeDependencies(), parse.getSpannedWords(),
          parse.getNodeProbability(), parse.getUnaryRule(),
          parse.getSpanStart(), parse.getSpanEnd()));
    } else {
      return Arrays.asList(CcgParse.forNonterminal(newSyntax, parse.getSemanticHeads(),
          parse.getNodeDependencies(), parse.getNodeProbability(), parse.getLeft(), parse.getRight(),
          parse.getCombinator(), parse.getUnaryRule(), parse.getSpanStart(), parse.getSpanEnd()));
    }
  }
}
