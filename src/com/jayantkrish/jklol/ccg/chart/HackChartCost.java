package com.jayantkrish.jklol.ccg.chart;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.SyntacticCategory;
import com.jayantkrish.jklol.models.DiscreteVariable;


/**
 * Hack chart cost used for imposing custom constraints on stuff.
 * 
 * @author jayantk
 *
 */
public class HackChartCost implements ChartCost {

  private final List<String> words;
  private final List<String> pos;
  
  private static final SyntacticCategory PREP_FINAL = SyntacticCategory.parseFrom("((S[0]\\NP)\\(S[0]\\NP))\\NP");
  private static final SyntacticCategory VBZ_START = SyntacticCategory.parseFrom("((S[0]/NP)/NP)");

  public HackChartCost(List<String> words, List<String> pos) {
    this.words = ImmutableList.copyOf(words);
    this.pos = ImmutableList.copyOf(pos);
  }
  
  @Override
  public double apply(ChartEntry entry, int spanStart, int spanEnd, DiscreteVariable syntaxType) {
    // Root of the parse must be atomic.
    if (spanStart == 0 && spanEnd == words.size() - 1) {
      HeadedSyntacticCategory headedSyntax = (HeadedSyntacticCategory) syntaxType.getValue(entry.getHeadedSyntax());
      if (!headedSyntax.isAtomic()) {
        return Double.NEGATIVE_INFINITY;
      }
    }
    
    if (spanStart == spanEnd && spanEnd == words.size() - 2 && pos.get(spanEnd).equals("IN") &&
        pos.get(spanEnd + 1).equals(".")) {
      HeadedSyntacticCategory headedSyntax = (HeadedSyntacticCategory) syntaxType.getValue(entry.getHeadedSyntax());
      SyntacticCategory syntax = headedSyntax.getSyntax().discardFeaturePassingMarkup();
      /*
      if (!PREP_FINAL.isUnifiableWith(syntax)) {
        // System.out.println("NO "+ syntax);
        return Double.NEGATIVE_INFINITY;
      } else {
        // System.out.println("YES "+ syntax);
      }
      */
    }
    
    if (spanStart == spanEnd && spanStart == 0 && pos.get(spanStart).equals("VBZ")) {
      HeadedSyntacticCategory headedSyntax = (HeadedSyntacticCategory) syntaxType.getValue(entry.getHeadedSyntax());
      SyntacticCategory syntax = headedSyntax.getSyntax().discardFeaturePassingMarkup();
      if (!VBZ_START.isUnifiableWith(syntax)) {
        return Double.NEGATIVE_INFINITY;
      }
    }

    return 0.0;
  }
}
