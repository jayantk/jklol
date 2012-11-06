package com.jayantkrish.jklol.ccg;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import au.com.bytecode.opencsv.CSVParser;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.jayantkrish.jklol.ccg.CcgChart.ChartEntry;

/**
 * A binary combination rule applicable to two adjacent CCG categories. These
 * rules represent operations like type-changing, which can be applied in
 * addition to the standard CCG application/combination rules. For example,
 * {@code CcgBinaryRule} can be used to absorb punctuation marks.
 * <p>
 * Each combination rule matches a pair of adjacent {@code SyntacticCategory}s,
 * and returns a new category for their span. The returned category may inherit
 * some of its semantics from one of the combined categories. In addition, the
 * semantics of the returned category may be augmented with additional
 * unfilled dependencies. The inherited semantics enable {@code CcgBinaryRule} to 
 * capture comma conjunction (for example).
 * 
 * @author jayantk
 */
public class CcgBinaryRule {
  private final HeadedSyntacticCategory leftSyntax;
  private final HeadedSyntacticCategory rightSyntax;
  private final HeadedSyntacticCategory returnSyntax;

  // Unfilled dependencies to add to the returned category,
  // in addition to any inherited heads and dependencies.
  private final long[] unfilledDeps;

  private static final char ENTRY_DELIMITER = ',';

  public CcgBinaryRule(HeadedSyntacticCategory leftSyntax, HeadedSyntacticCategory rightSyntax,
      HeadedSyntacticCategory returnSyntax, long[] unfilledDeps) {
    this.leftSyntax = leftSyntax;
    this.rightSyntax = rightSyntax;
    this.returnSyntax = returnSyntax;

    this.unfilledDeps = unfilledDeps;
  }

  /**
   * Parses a binary rule from a line in comma-separated format. The expected
   * fields, in order, are:
   * <ul>
   * <li>The headed syntactic categories to combine and return:
   * <code>(left syntax) (right syntax) (return syntax)</code>
   * <li>(optional) Additional unfilled dependencies, in standard format:
   * <code>(predicate) (argument number) (argument variable)</code>
   * </ul>
   * 
   * For example, ", NP{0} NP{0}" is a binary rule that allows an NP to absorb a
   * comma on the left. Or, "conj{2} (S{0}\NP{1}){0} ((S{0}\NP{1}){0}\(S{0}\NP{1}){0}){2}"
   * allows the "conj" type to conjoin verb phrases of type (S\NP).
   * 
   * @param line
   * @return
   */
  public static CcgBinaryRule parseFrom(String line) {
    try {
      String[] chunks = new CSVParser(ENTRY_DELIMITER, CSVParser.DEFAULT_QUOTE_CHARACTER,
          CSVParser.NULL_CHARACTER).parseLine(line);
      Preconditions.checkArgument(chunks.length >= 1);

      System.out.println(Arrays.toString(chunks));

      String[] syntacticParts = chunks[0].split(" ");
      Preconditions.checkArgument(syntacticParts.length == 3);
      HeadedSyntacticCategory leftSyntax = HeadedSyntacticCategory.parseFrom(syntacticParts[0]);
      HeadedSyntacticCategory rightSyntax = HeadedSyntacticCategory.parseFrom(syntacticParts[1]);
      HeadedSyntacticCategory returnSyntax = HeadedSyntacticCategory.parseFrom(syntacticParts[2]);

      long[] unfilledDeps = new long[0];
      if (chunks.length >= 2) {
        throw new UnsupportedOperationException("Not yet implemented");
        /*
        String[] newDeps = chunks[4].split(" ");
        Preconditions.checkArgument(newDeps.length == 3);
        long subjectNum = Long.parseLong(newDeps[0].substring(1));
        long argNum = Long.parseLong(newDeps[1]);
        long objectNum = Long.parseLong(newDeps[2].substring(1));
        unfilledDeps = new long[1];

        unfilledDeps[0] = CcgParser.marshalUnfilledDependency(objectNum, argNum, subjectNum, 0, 0);
        */
      }

      return new CcgBinaryRule(leftSyntax, rightSyntax, returnSyntax, unfilledDeps);
    } catch (IOException e) {
      throw new IllegalArgumentException("Illegal binary rule string: " + line, e);
    }
  }

  /**
   * Gets the expected syntactic type that should occur on the left side in
   * order to instantiate this rule.
   * 
   * @return
   */
  public SyntacticCategory getLeftSyntacticType() {
    return leftSyntax.getSyntax();
  }

  /**
   * Gets the expected syntactic type that should occur on the right side in
   * order to instantiate this rule.
   * 
   * @return
   */
  public SyntacticCategory getRightSyntacticType() {
    return rightSyntax.getSyntax();
  }

  /**
   * Combines {@code left} and {@code right} using this rule, returning a new
   * {@code ChartEntry} spanning the two.
   * 
   * @param left
   * @param right
   * @param leftSpanStart
   * @param leftSpanEnd
   * @param leftIndex
   * @param rightSpanStart
   * @param rightSpanEnd
   * @param rightIndex
   * @return
   */
  public ChartEntry apply(ChartEntry left, ChartEntry right, int leftSpanStart,
      int leftSpanEnd, int leftIndex, int rightSpanStart, int rightSpanEnd,
      int rightIndex) {

    HeadedSyntacticCategory leftChartSyntax = left.getHeadedSyntax();
    HeadedSyntacticCategory rightChartSyntax = right.getHeadedSyntax();
    
    int[] leftPatternToChart = leftChartSyntax.unifyVariables(leftSyntax);
    int[] rightPatternToChart = rightChartSyntax.unifyVariables(rightSyntax);
    
    if (leftPatternToChart == null || rightPatternToChart == null) {
      return null;
    }
    
    int[] leftVars = left.getAssignmentVariableNumsRelabeled(leftPatternToChart);
    int[] leftPredicateNums = left.getAssignmentPredicateNums();
    int[] leftIndexes = left.getAssignmentIndexes();
    long[] leftUnfilledDeps = left.getUnfilledDependenciesRelabeled(leftPatternToChart);
    
    int[] rightVars = right.getAssignmentVariableNumsRelabeled(rightPatternToChart);
    int[] rightPredicateNums = right.getAssignmentPredicateNums();
    int[] rightIndexes = right.getAssignmentIndexes();
    long[] rightUnfilledDeps = right.getUnfilledDependenciesRelabeled(rightPatternToChart);
    
    int[] returnUniqueVars = returnSyntax.getUniqueVariables();
    long[] returnUnfilledDeps = Longs.concat(leftUnfilledDeps, rightUnfilledDeps);
    List<Integer> returnVars = Lists.newArrayList();
    List<Integer> returnPredicateNums = Lists.newArrayList();
    List<Integer> returnIndexes = Lists.newArrayList();
    
    for (int i = 0; i < leftVars.length; i++) {
      for (int j = 0; j < returnUniqueVars.length; j++) {
        if (leftVars[i] == returnUniqueVars[j]) {
          returnVars.add(leftVars[i]);
          returnPredicateNums.add(leftPredicateNums[i]);
          returnIndexes.add(leftIndexes[i]);
        }
      }
    }
    
    for (int i = 0; i < rightVars.length; i++) {
      for (int j = 0; j < returnUniqueVars.length; j++) {
        if (rightVars[i] == returnUniqueVars[j]) {
          returnVars.add(rightVars[i]);
          returnPredicateNums.add(rightPredicateNums[i]);
          returnIndexes.add(rightIndexes[i]);
        }
      }
    }
    
    System.out.println("left unfilled: " + Arrays.toString(leftUnfilledDeps));
    System.out.println("right unfilled: " + Arrays.toString(rightUnfilledDeps));
    System.out.println("unfilled: " + Arrays.toString(returnUnfilledDeps));

    return new ChartEntry(returnSyntax, Ints.toArray(returnVars), Ints.toArray(returnPredicateNums), 
        Ints.toArray(returnIndexes), returnUnfilledDeps, new long[0], leftSpanStart, leftSpanEnd, 
        leftIndex, rightSpanStart, rightSpanEnd, rightIndex);
  }
}
