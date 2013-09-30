package com.jayantkrish.jklol.ccg.chart;

import java.util.List;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.ccg.CcgSyntaxTree;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.SyntacticCategory;
import com.jayantkrish.jklol.models.DiscreteVariable;

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
  private final Map<Integer, SyntacticCategory> leftUnaryRuleResult;
  private final Map<Integer, SyntacticCategory> rightUnaryRuleResult;
  private final SyntacticCategory expectedPostUnaryRoot;

  private final CcgSyntaxTree parse;
  private final List<HeadedSyntacticCategory> headedTerminals;

  private static final int SPAN_START_OFFSET = 100000;

  public SyntacticChartFilter(CcgSyntaxTree syntacticParse) {
    this.binaryRuleResult = Maps.newHashMap();
    this.leftUnaryRuleResult = Maps.newHashMap();
    this.rightUnaryRuleResult = Maps.newHashMap();
    this.expectedPostUnaryRoot = syntacticParse.getRootSyntax();

    this.parse = syntacticParse;
    this.headedTerminals = syntacticParse.getAllSpannedHeadedSyntacticCategories();

    populateRuleMaps(syntacticParse);
  }

  private void populateRuleMaps(CcgSyntaxTree parse) {
    int mapIndex = (parse.getSpanStart() * SPAN_START_OFFSET) + parse.getSpanEnd();

    binaryRuleResult.put(mapIndex, parse.getPreUnaryRuleSyntax());

    if (!parse.isTerminal()) {
      CcgSyntaxTree leftTree = parse.getLeft();
      populateRuleMaps(leftTree);
      if (leftTree.hasUnaryRule()) {
        leftUnaryRuleResult.put(mapIndex, leftTree.getRootSyntax());
      }

      CcgSyntaxTree rightTree = parse.getRight();
      populateRuleMaps(rightTree);
      if (rightTree.hasUnaryRule()) {
        rightUnaryRuleResult.put(mapIndex, rightTree.getRootSyntax());
      }
    }
  }

  @Override
  public boolean apply(ChartEntry entry, int spanStart, int spanEnd, DiscreteVariable syntaxVarType) {
    int mapIndex = (spanStart * SPAN_START_OFFSET) + spanEnd;
    if (!binaryRuleResult.containsKey(mapIndex)) {
      return false;
    }

    if (entry.getRootUnaryRule() != null) {
      Preconditions.checkState(spanStart == parse.getSpanStart() && spanEnd == parse.getSpanEnd());
      if (!isSyntaxCompatible(expectedPostUnaryRoot, entry.getHeadedSyntax(), syntaxVarType)) {
        return false;
      }
    } else {
      SyntacticCategory expectedRootSyntax = binaryRuleResult.get(mapIndex);
      if (!isSyntaxCompatible(expectedRootSyntax, entry.getHeadedSyntax(), syntaxVarType)) {
        return false;
      }
    }

    if (leftUnaryRuleResult.containsKey(mapIndex)) {
      SyntacticCategory expectedLeft = leftUnaryRuleResult.get(mapIndex);
      if (entry.getLeftUnaryRule() == null || !isSyntaxCompatible(expectedLeft, entry.getLeftUnaryRule().getSyntax(), syntaxVarType)) {
        return false;
      }
    } else if (entry.getLeftUnaryRule() != null) {
      return false;
    }

    if (rightUnaryRuleResult.containsKey(mapIndex)) {
      SyntacticCategory expectedRight = rightUnaryRuleResult.get(mapIndex);
      if (entry.getRightUnaryRule() == null || !isSyntaxCompatible(expectedRight, entry.getRightUnaryRule().getSyntax(), syntaxVarType)) {
        return false;
      }
    } else if (entry.getRightUnaryRule() != null) {
      return false;
    }
    
    if (spanStart == spanEnd) {
      // Terminals may have a specified headed syntactic
      // category in the parse tree.
      HeadedSyntacticCategory expectedHeadedSyntax = headedTerminals.get(spanStart);
      if (expectedHeadedSyntax != null) {
        HeadedSyntacticCategory actual =(HeadedSyntacticCategory) syntaxVarType.getValue(entry.getHeadedSyntax());

        if (!actual.equals(expectedHeadedSyntax)) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * This method doesn't modify {@code chart}.
   */
  @Override
  public void applyToTerminals(CcgChart chart) {
  }

  private boolean isSyntaxCompatible(SyntacticCategory expected, int actual, DiscreteVariable syntaxType) {
    HeadedSyntacticCategory headedSyntax = (HeadedSyntacticCategory) syntaxType.getValue(actual);
    SyntacticCategory syntax = headedSyntax.getSyntax().assignAllFeatures(SyntacticCategory.DEFAULT_FEATURE_VALUE);
    return expected.equals(syntax);
  }
}
