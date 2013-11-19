package com.jayantkrish.jklol.ccg;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.jayantkrish.jklol.ccg.chart.CcgBeamSearchChart;
import com.jayantkrish.jklol.ccg.chart.CcgChart;
import com.jayantkrish.jklol.ccg.chart.ChartEntry;
import com.jayantkrish.jklol.ccg.chart.SyntacticChartFilter;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

/**
 * Utility functions for CCG parsers.
 * 
 * @author jayantk
 */
public class CcgParserUtils {

  /**
   * Checks whether each example in {@code examples} can be produced
   * by this parser. If {@code errorOnInvalidExample = true}, then
   * this method throws an error if an invalid example is encountered.
   * Otherwise, invalid examples are simply filtered out of the
   * returned examples.
   * 
   * @param examples
   * @param errorOnInvalidExample
   * @return
   */
  public static <T extends CcgExample> List<T> filterExampleCollection(CcgParser parser, Iterable<T> examples,
      boolean errorOnInvalidExample, Multimap<SyntacticCategory, HeadedSyntacticCategory> syntacticCategoryMap) {
    List<T> filteredExamples = Lists.newArrayList();
    for (T example : examples) {
      // isPossibleExample(example, syntacticCategoryMap)
      if (isPossibleExample(parser, example)) {
        filteredExamples.add(example);
      } else {
        Preconditions.checkState(!errorOnInvalidExample, "Invalid example: %s", example);
        System.out.println("Discarding example: " + example);
      }
    }
    return filteredExamples;
  }

  /**
   * Returns {@code true} if {@code parser} can reproduce the
   * syntactic tree in {@code example}. 
   * 
   * @param parser
   * @param example
   * @return
   */
  public static boolean isPossibleExample(CcgParser parser, CcgExample example) {
    // CcgChart chart = new CcgExactHashTableChart(example.getWords(), example.getPosTags());
    CcgBeamSearchChart chart = new CcgBeamSearchChart(example.getWords(), example.getPosTags(),
        100, Integer.MAX_VALUE);
    SyntacticChartFilter filter = new SyntacticChartFilter(example.getSyntacticParse());
    parser.parseCommon(chart, example.getWords(), example.getPosTags(), filter, null, -1);
    List<CcgParse> parses = chart.decodeBestParsesForSpan(0, example.getWords().size() - 1, 100, parser);
    if (parses.size() == 0) {
      // Provide a deeper analysis of why parsing failed.
      analyzeParseFailure(example.getSyntacticParse(), chart, parser.getSyntaxVarType(), "Parse failure", 0);
      return false;
    } else if (parses.size() > 1) {
      analyzeParseFailure(example.getSyntacticParse(), chart, parser.getSyntaxVarType(), "Parse duplication", 2);
      System.out.println("Duplicate correct parse: " + example.getSyntacticParse());
    }
    return true;
  }

  public static boolean analyzeParseFailure(CcgSyntaxTree tree, CcgChart chart,
      DiscreteVariable syntaxVarType, String errorMessage, int failureNum) {
    boolean foundFailurePoint = false;
    if (!tree.isTerminal()) {
      foundFailurePoint = foundFailurePoint || analyzeParseFailure(tree.getLeft(), chart,
          syntaxVarType, errorMessage, failureNum);
      foundFailurePoint = foundFailurePoint || analyzeParseFailure(tree.getRight(), chart,
          syntaxVarType, errorMessage, failureNum);
    }

    if (foundFailurePoint) {
      return true;
    } else {
      int spanStart = tree.getSpanStart();
      int spanEnd = tree.getSpanEnd();

      int numChartEntries = chart.getNumChartEntriesForSpan(spanStart, spanEnd);
      if (numChartEntries == failureNum) {
        if (tree.isTerminal()) {
          System.out.println(errorMessage + " terminal: " + tree.getWords() + " -> " +
              tree.getRootSyntax() + " headed: " + tree.getHeadedSyntacticCategory());
        } else {
          System.out.println(errorMessage + " nonterminal: " + tree.getLeft().getRootSyntax() + " "
              + tree.getRight().getRootSyntax() + " -> " + tree.getRootSyntax());
          StringBuilder sb = new StringBuilder();
          sb.append("left entries: ");
          ChartEntry[] leftEntries = chart.getChartEntriesForSpan(tree.getLeft().getSpanStart(), tree.getLeft().getSpanEnd());
          int numLeftEntries = chart.getNumChartEntriesForSpan(tree.getLeft().getSpanStart(), tree.getLeft().getSpanEnd());
          for (int i = 0; i < numLeftEntries; i++) {
            sb.append(syntaxVarType.getValue(leftEntries[i].getHeadedSyntax()));
            sb.append(" ");
          }
          System.out.println(sb.toString());
          sb = new StringBuilder();
          sb.append("right entries: ");
          ChartEntry[] rightEntries = chart.getChartEntriesForSpan(tree.getRight().getSpanStart(), tree.getRight().getSpanEnd());
          int numRightEntries = chart.getNumChartEntriesForSpan(tree.getRight().getSpanStart(), tree.getRight().getSpanEnd());
          for (int i = 0; i < numRightEntries; i++) {
            sb.append(syntaxVarType.getValue(rightEntries[i].getHeadedSyntax()));
            sb.append(" ");
          }
          System.out.println(sb.toString());
        }
        return true;
      } else {
        return false;
      }
    }
  }

  public static <T extends CcgExample> SufficientStatistics getFeatureCounts(
      ParametricCcgParser family, Iterable<T> examples) {
    CcgParser parser = family.getModelFromParameters(family.getNewSufficientStatistics());
    SufficientStatistics featureCounts = family.getNewSufficientStatistics();
    for (CcgExample example : examples) {
      CcgBeamSearchChart chart = new CcgBeamSearchChart(example.getWords(), example.getPosTags(),
          100, Integer.MAX_VALUE);
      SyntacticChartFilter filter = new SyntacticChartFilter(example.getSyntacticParse());
      parser.parseCommon(chart, example.getWords(), example.getPosTags(), filter, null, -1);
      List<CcgParse> parses = chart.decodeBestParsesForSpan(0, example.getWords().size() - 1, 100, parser);
      if (parses.size() > 0) {
        family.incrementSufficientStatistics(featureCounts, parses.get(0), 1.0);
      }
    }
    return featureCounts;
  }

  private CcgParserUtils() {
    // Prevent instantiation.
  }
}
