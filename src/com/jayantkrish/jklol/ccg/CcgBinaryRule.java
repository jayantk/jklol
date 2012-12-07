package com.jayantkrish.jklol.ccg;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

import au.com.bytecode.opencsv.CSVParser;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.jayantkrish.jklol.ccg.CcgChart.ChartEntry;

/**
 * A binary combination rule applicable to two adjacent CCG
 * categories. These rules represent operations like type-changing,
 * which can be applied in addition to the standard CCG
 * application/combination rules. For example, {@code CcgBinaryRule}
 * can be used to absorb punctuation marks.
 * <p>
 * Each combination rule matches a pair of adjacent
 * {@code SyntacticCategory}s, and returns a new category for their
 * span. The returned category may inherit some of its semantics from
 * one of the combined categories. In addition, the semantics of the
 * returned category may be augmented with additional unfilled
 * dependencies. The inherited semantics enable {@code CcgBinaryRule}
 * to capture comma conjunction (for example).
 * 
 * @author jayantk
 */
public class CcgBinaryRule implements Serializable {
  
  private static final long serialVersionUID = 2L;
  
  private final HeadedSyntacticCategory leftSyntax;
  private final HeadedSyntacticCategory rightSyntax;
  private final HeadedSyntacticCategory returnSyntax;

  // Unfilled dependencies created by this rule.
  private final String[] subjects;
  private final int[] argumentNumbers;
  // The variables each dependency accepts.
  private final int[] objects;

  private static final char ENTRY_DELIMITER = ',';

  public CcgBinaryRule(HeadedSyntacticCategory leftSyntax, HeadedSyntacticCategory rightSyntax,
      HeadedSyntacticCategory returnSyntax, List<String> subjects, List<Integer> argumentNumbers,
      List<Integer> objects) {
    this.leftSyntax = leftSyntax;
    this.rightSyntax = rightSyntax;
    this.returnSyntax = returnSyntax;

    this.subjects = subjects.toArray(new String[0]);
    this.argumentNumbers = Ints.toArray(argumentNumbers);
    this.objects = Ints.toArray(objects);
  }

  /**
   * Parses a binary rule from a line in comma-separated format. The
   * expected fields, in order, are:
   * <ul>
   * <li>The headed syntactic categories to combine and return:
   * <code>(left syntax) (right syntax) (return syntax)</code>
   * <li>(optional) Additional unfilled dependencies, in standard
   * format:
   * <code>(predicate) (argument number) (argument variable)</code>
   * </ul>
   * 
   * For example, ", NP{0} NP{0}" is a binary rule that allows an NP
   * to absorb a comma on the left. Or, "conj{2} (S{0}\NP{1}){0}
   * ((S{0}\NP{1}){0}\(S{0}\NP{1}){0}){2}" allows the "conj" type to
   * conjoin verb phrases of type (S\NP).
   * 
   * @param line
   * @return
   */
  public static CcgBinaryRule parseFrom(String line) {
    try {
      String[] chunks = new CSVParser(ENTRY_DELIMITER, CSVParser.DEFAULT_QUOTE_CHARACTER,
          CSVParser.NULL_CHARACTER).parseLine(line.trim());
      Preconditions.checkArgument(chunks.length >= 1);

      String[] syntacticParts = chunks[0].split(" ");
      Preconditions.checkArgument(syntacticParts.length == 3);
      HeadedSyntacticCategory leftSyntax = HeadedSyntacticCategory.parseFrom(syntacticParts[0]);
      HeadedSyntacticCategory rightSyntax = HeadedSyntacticCategory.parseFrom(syntacticParts[1]);
      HeadedSyntacticCategory returnSyntax = HeadedSyntacticCategory.parseFrom(syntacticParts[2]);

      List<String> subjects = Lists.newArrayList();
      List<Integer> argNums = Lists.newArrayList();
      List<Integer> objects = Lists.newArrayList();
      if (chunks.length >= 2) {
        for (int i = 1; i < chunks.length; i++) {
          String[] newDeps = chunks[i].split(" ");
          Preconditions.checkArgument(newDeps.length == 3);
          subjects.add(newDeps[0]);
          argNums.add(Integer.parseInt(newDeps[1]));
          objects.add(Integer.parseInt(newDeps[2]));
        }
      }

      return new CcgBinaryRule(leftSyntax, rightSyntax, returnSyntax, subjects, argNums, objects);
    } catch (IOException e) {
      throw new IllegalArgumentException("Illegal binary rule string: " + line, e);
    }
  }

  /**
   * Gets the expected syntactic type that should occur on the left
   * side in order to instantiate this rule.
   * 
   * @return
   */
  public SyntacticCategory getLeftSyntacticType() {
    return leftSyntax.getSyntax();
  }

  /**
   * Gets the expected syntactic type that should occur on the right
   * side in order to instantiate this rule.
   * 
   * @return
   */
  public SyntacticCategory getRightSyntacticType() {
    return rightSyntax.getSyntax();
  }

  /**
   * Gets the list of subjects of the dependencies instantiated by
   * this rule.
   * 
   * @return
   */
  public List<String> getSubjects() {
    return Arrays.asList(subjects);
  }

  /**
   * Gets the list of argument numbers of the dependencies
   * instantiated by this rule.
   * 
   * @return
   */
  public List<Integer> getArgumentNumbers() {
    return Ints.asList(argumentNumbers);
  }

  /**
   * Gets the list of object variable numbers of the dependencies
   * instantiated by this rule.
   * 
   * @return
   */
  public List<Integer> getObjects() {
    return Ints.asList(objects);
  }

  /**
   * Combines {@code left} and {@code right} using this rule,
   * returning a new {@code ChartEntry} spanning the two.
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
      int rightIndex, CcgParser parser) {

    HeadedSyntacticCategory leftChartSyntax = left.getHeadedSyntax();
    HeadedSyntacticCategory rightChartSyntax = right.getHeadedSyntax();

    int[] leftPatternToChart = leftChartSyntax.unifyVariables(
        leftChartSyntax.getUniqueVariables(), leftSyntax, new int[0]);
    int[] rightPatternToChart = rightChartSyntax.unifyVariables(
        rightChartSyntax.getUniqueVariables(), rightSyntax, new int[0]);

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

    // Determine which variables from the left and right syntactic
    // types are retained in the returned assignment.
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

    // Fill the binary rule dependencies.
    long[] filledDepArray = new long[0];
    if (objects.length > 0) {
      List<Long> filledDeps = Lists.newArrayList();
      for (int i = 0; i < objects.length; i++) {
        int leftVarIndex = Ints.indexOf(leftVars, objects[i]);
        int rightVarIndex = Ints.indexOf(rightVars, objects[i]);

        if (leftVarIndex != -1) {
          long subjectNum = parser.predicateToLong(subjects[i]);
          long objectNum = leftPredicateNums[leftVarIndex];

          filledDeps.add(CcgParser.marshalFilledDependency(objectNum, argumentNumbers[i],
              subjectNum, leftSpanEnd, rightSpanStart));
        }

        if (rightVarIndex != -1) {
          long subjectNum = parser.predicateToLong(subjects[i]);
          long objectNum = rightPredicateNums[rightVarIndex];

          filledDeps.add(CcgParser.marshalFilledDependency(objectNum, argumentNumbers[i],
              subjectNum, rightSpanStart, rightSpanStart));
        }
      }
      filledDepArray = Longs.toArray(filledDeps);
    }

    return new ChartEntry(returnSyntax, null, Ints.toArray(returnVars), Ints.toArray(returnPredicateNums),
        Ints.toArray(returnIndexes), returnUnfilledDeps, filledDepArray, leftSpanStart, leftSpanEnd,
        leftIndex, rightSpanStart, rightSpanEnd, rightIndex);
  }
}
