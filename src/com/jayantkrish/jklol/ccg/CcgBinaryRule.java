package com.jayantkrish.jklol.ccg;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

import au.com.bytecode.opencsv.CSVParser;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;

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
  private final HeadedSyntacticCategory parentSyntax;

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
    this.parentSyntax = returnSyntax;

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
   * side in order to instantiate this rule. The returned type may not
   * be in canonical form.
   * 
   * @return
   */
  public HeadedSyntacticCategory getLeftSyntacticType() {
    return leftSyntax;
  }

  /**
   * Gets the expected syntactic type that should occur on the right
   * side in order to instantiate this rule. The returned type may not
   * be in canonical form.
   * 
   * @return
   */
  public HeadedSyntacticCategory getRightSyntacticType() {
    return rightSyntax;
  }

  /**
   * Gets the syntactic type that is produced by this rule. The returned
   * type may not be in canonical form.
   * 
   * @return
   */
  public HeadedSyntacticCategory getParentSyntacticType() {
    return parentSyntax;
  }

  /**
   * Gets the list of subjects of the dependencies instantiated by
   * this rule.
   * 
   * @return
   */
  public String[] getSubjects() {
    return subjects;
  }

  /**
   * Gets the list of argument numbers of the dependencies
   * instantiated by this rule.
   * 
   * @return
   */
  public int[] getArgumentNumbers() {
    return argumentNumbers;
  }

  /**
   * Gets the list of object variable numbers of the dependencies
   * instantiated by this rule.
   * 
   * @return
   */
  public int[] getObjects() {
    return objects;
  }
  
  @Override
  public String toString() {
    return leftSyntax + " " + rightSyntax + " -> " + parentSyntax + ", " + Arrays.toString(subjects);
  }
}
