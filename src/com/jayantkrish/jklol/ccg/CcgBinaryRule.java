package com.jayantkrish.jklol.ccg;

import java.io.IOException;
import java.util.Arrays;

import au.com.bytecode.opencsv.CSVParser;

import com.google.common.base.Preconditions;
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
 * semantics of the returned category may be augmented with some additional
 * (unfilled) heads and dependencies. These augmentations enable
 * {@code CcgBinaryRule} to capture comma conjunction (for example).
 * 
 * @author jayantk
 */
public class CcgBinaryRule {
  private final SyntacticCategory leftSyntax;
  private final SyntacticCategory rightSyntax;
  private final SyntacticCategory returnSyntax;

  // Unfilled heads or dependencies to add to the returned category,
  // in addition to any inherited heads and dependencies.
  private final int[] unfilledHeads;
  private final long[] unfilledDeps;

  // If true, this rule inherits its semantic heads and dependencies from
  // the left (right) category.
  private final boolean inheritLeft;
  private final boolean inheritRight;

  private static final char ENTRY_DELIMITER = ',';

  public CcgBinaryRule(SyntacticCategory leftSyntax, SyntacticCategory rightSyntax,
      SyntacticCategory returnSyntax, int[] unfilledHeads, long[] unfilledDeps,
      boolean inheritLeft, boolean inheritRight) {
    this.leftSyntax = leftSyntax;
    this.rightSyntax = rightSyntax;
    this.returnSyntax = returnSyntax;

    this.unfilledHeads = unfilledHeads;
    this.unfilledDeps = unfilledDeps;

    this.inheritLeft = inheritLeft;
    this.inheritRight = inheritRight;
  }

  /**
   * Parses a binary rule from a line in comma-separated format. The expected
   * fields, in order, are:
   * <ul>
   * <li>(left syntax) (right syntax) (return syntax)
   * <li>Whether to inherit semantics from the left category (either "T" or "F")
   * <li>Whether to inherit semantics from the right category (either "T" or
   * "F")
   * <li>(optional) Additional head variables, in the standard ?(variable num)
   * format.
   * <li>(optional) Additional unfilled dependencies, in standard ?(num) 2
   * ?(num) format
   * </ul>
   * 
   * For example, ", NP NP,F,T" is a binary rule that allows NPs to absorb a
   * comma on the left. Or, "conj (S\NP) (S\NP)\(S\NP),F,T,?2,?2 1
   * ?1" allows the "conj" type to conjoin verb phrases of type (S\NP).
   * 
   * @param line
   * @return
   */
  public static CcgBinaryRule parseFrom(String line) {
    try {
      String[] chunks = new CSVParser(ENTRY_DELIMITER, CSVParser.DEFAULT_QUOTE_CHARACTER,
          CSVParser.NULL_CHARACTER).parseLine(line);
      Preconditions.checkArgument(chunks.length >= 3);

      System.out.println(Arrays.toString(chunks));

      String[] syntacticParts = chunks[0].split(" ");
      Preconditions.checkArgument(syntacticParts.length == 3);
      SyntacticCategory leftSyntax = SyntacticCategory.parseFrom(syntacticParts[0]);
      SyntacticCategory rightSyntax = SyntacticCategory.parseFrom(syntacticParts[1]);
      SyntacticCategory returnSyntax = SyntacticCategory.parseFrom(syntacticParts[2]);

      boolean inheritLeft = chunks[1].equals("T");
      boolean inheritRight = chunks[2].equals("T");

      int[] unfilledHeads = new int[0];
      if (chunks.length >= 4) {
        String[] newHeads = chunks[3].split(",");
        unfilledHeads = new int[newHeads.length];
        for (int i = 0; i < newHeads.length; i++) {
          unfilledHeads[i] = Integer.parseInt(newHeads[i].substring(1));
        }
      }

      long[] unfilledDeps = new long[0];
      if (chunks.length >= 5) {
        String[] newDeps = chunks[4].split(" ");
        Preconditions.checkArgument(newDeps.length == 3);
        long subjectNum = Long.parseLong(newDeps[0].substring(1));
        long argNum = Long.parseLong(newDeps[1]);
        long objectNum = Long.parseLong(newDeps[2].substring(1));
        unfilledDeps = new long[1];

        unfilledDeps[0] = CcgParser.marshalUnfilledDependency(objectNum, argNum, subjectNum, 0, 0);
      }

      return new CcgBinaryRule(leftSyntax, rightSyntax, returnSyntax, unfilledHeads, unfilledDeps,
          inheritLeft, inheritRight);
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
    return leftSyntax;
  }

  /**
   * Gets the expected syntactic type that should occur on the right side in
   * order to instantiate this rule.
   * 
   * @return
   */
  public SyntacticCategory getRightSyntacticType() {
    return rightSyntax;
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

    int[] newHeadNums = new int[0];
    int[] newHeadIndexes = new int[0];
    int[] newUnfilledHeads = Arrays.copyOf(unfilledHeads, unfilledHeads.length);
    long[] newUnfilledDeps = Arrays.copyOf(unfilledDeps, unfilledDeps.length);
    long[] newFilledDeps = new long[0];
    if (inheritLeft) {
      newHeadNums = Ints.concat(newHeadNums, left.getHeadWordNums());
      newHeadIndexes = Ints.concat(newHeadIndexes, left.getHeadIndexes());
      newUnfilledHeads = Ints.concat(newUnfilledHeads, left.getUnfilledHeads());
      newUnfilledDeps = Longs.concat(newUnfilledDeps, left.getUnfilledDependencies());
    }
    if (inheritRight) {
      newHeadNums = Ints.concat(newHeadNums, right.getHeadWordNums());
      newHeadIndexes = Ints.concat(newHeadIndexes, right.getHeadIndexes());
      newUnfilledHeads = Ints.concat(newUnfilledHeads, right.getUnfilledHeads());
      newUnfilledDeps = Longs.concat(newUnfilledDeps, right.getUnfilledDependencies());
    }

    return new ChartEntry(returnSyntax, newHeadNums, newHeadIndexes, newUnfilledHeads,
        newUnfilledDeps, newFilledDeps, leftSpanStart, leftSpanEnd, leftIndex, rightSpanStart,
        rightSpanEnd, rightIndex);
  }
}
