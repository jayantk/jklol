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
 * A cost derived from a given syntactic CCG parse tree. Entries in
 * the parse chart which also occur in the given parse are given one
 * cost, and entries that disagree are given a different cost. This
 * cost can be used to force the parser to agree with a given
 * syntactic tree, or also to encode a max-margin cost.
 * 
 * @author jayantk
 */
public class SyntacticChartCost implements ChartCost {

  private final Map<Integer, SyntacticCategory> binaryRuleResult;
  private final Map<Integer, SyntacticCategory> leftUnaryRuleResult;
  private final Map<Integer, SyntacticCategory> rightUnaryRuleResult;
  private final SyntacticCategory expectedPostUnaryRoot;

  private final CcgSyntaxTree parse;
  private final List<HeadedSyntacticCategory> headedTerminals;
  
  private final double agreeCost;
  private final double disagreeCost;

  private static final int SPAN_START_OFFSET = 100000;

  public SyntacticChartCost(CcgSyntaxTree syntacticParse, double agreeCost, double disagreeCost) {
    this.binaryRuleResult = Maps.newHashMap();
    this.leftUnaryRuleResult = Maps.newHashMap();
    this.rightUnaryRuleResult = Maps.newHashMap();
    this.expectedPostUnaryRoot = syntacticParse.getRootSyntax();

    this.parse = syntacticParse;
    this.headedTerminals = syntacticParse.getAllSpannedHeadedSyntacticCategories();
    
    this.agreeCost = agreeCost;
    this.disagreeCost = disagreeCost;

    populateRuleMaps(syntacticParse);
  }

  /**
   * Creates a cost function which forces the CCG parser to produce
   * the same tree structure as {@code syntacticParse}.
   * 
   * @param syntacticParse
   * @return
   */
  public static SyntacticChartCost createAgreementCost(CcgSyntaxTree syntacticParse) {
    return new SyntacticChartCost(syntacticParse, 0.0, Double.NEGATIVE_INFINITY);
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
  public double apply(ChartEntry entry, int spanStart, int spanEnd, DiscreteVariable syntaxVarType) {
    int mapIndex = (spanStart * SPAN_START_OFFSET) + spanEnd;
    if (!binaryRuleResult.containsKey(mapIndex)) {
      return disagreeCost;
    }

    if (entry.getRootUnaryRule() != null) {
      Preconditions.checkState(spanStart == parse.getSpanStart() && spanEnd == parse.getSpanEnd());
      if (!isSyntaxCompatible(expectedPostUnaryRoot, entry.getHeadedSyntax(), syntaxVarType)) {
        return disagreeCost;
      }
    } else {
      SyntacticCategory expectedRootSyntax = binaryRuleResult.get(mapIndex);
      if (!isSyntaxCompatible(expectedRootSyntax, entry.getHeadedSyntax(), syntaxVarType)) {
        return disagreeCost;
      }
    }

    if (leftUnaryRuleResult.containsKey(mapIndex)) {
      SyntacticCategory expectedLeft = leftUnaryRuleResult.get(mapIndex);
      if (entry.getLeftUnaryRule() == null || !isSyntaxCompatible(expectedLeft, entry.getLeftUnaryRule().getSyntax(), syntaxVarType)) {
        return disagreeCost;
      }
    } else if (entry.getLeftUnaryRule() != null) {
      return disagreeCost;
    }

    if (rightUnaryRuleResult.containsKey(mapIndex)) {
      SyntacticCategory expectedRight = rightUnaryRuleResult.get(mapIndex);
      if (entry.getRightUnaryRule() == null || !isSyntaxCompatible(expectedRight, entry.getRightUnaryRule().getSyntax(), syntaxVarType)) {
        return disagreeCost;
      }
    } else if (entry.getRightUnaryRule() != null) {
      return disagreeCost;
    }

    if (spanStart == spanEnd) {
      // Terminals may have a specified headed syntactic
      // category in the parse tree.
      HeadedSyntacticCategory expectedHeadedSyntax = headedTerminals.get(spanStart);
      if (expectedHeadedSyntax != null) {
        HeadedSyntacticCategory actual = (HeadedSyntacticCategory) syntaxVarType.getValue(entry.getHeadedSyntax());

        if (!actual.equals(expectedHeadedSyntax)) {
          return disagreeCost;
        }
      }
    }
    return agreeCost;
  }

  private boolean isSyntaxCompatible(SyntacticCategory expected, int actual, DiscreteVariable syntaxType) {
    HeadedSyntacticCategory headedSyntax = (HeadedSyntacticCategory) syntaxType.getValue(actual);
    SyntacticCategory syntax = headedSyntax.getSyntax().assignAllFeatures(SyntacticCategory.DEFAULT_FEATURE_VALUE);
    return expected.equals(syntax);
  }
}
