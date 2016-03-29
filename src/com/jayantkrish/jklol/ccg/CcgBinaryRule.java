package com.jayantkrish.jklol.ccg;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis;
import com.jayantkrish.jklol.util.CsvParser;

/**
 * A binary combination rule applicable to two adjacent CCG
 * categories. These rules represent operations like type-changing,
 * in addition to the standard CCG application/combination rules.
 * For example, {@code CcgBinaryRule} can be used to absorb
 * punctuation marks.
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

  // Logical form for this rule. The logical form is a function of
  // type (left lf -> (right lf -> result))
  private final Expression2 logicalForm;

  // Unfilled dependencies created by this rule.
  private final String[] subjects;
  private final HeadedSyntacticCategory[] subjectSyntacticCategories;
  private final int[] argumentNumbers;
  // The variables each dependency accepts.
  private final int[] objects;
  
  private final Combinator.Type type;

  public CcgBinaryRule(HeadedSyntacticCategory leftSyntax, HeadedSyntacticCategory rightSyntax,
      HeadedSyntacticCategory returnSyntax, Expression2 logicalForm, List<String> subjects,
      List<HeadedSyntacticCategory> subjectSyntaxes, List<Integer> argumentNumbers,
      List<Integer> objects, Combinator.Type type) {
    this.leftSyntax = leftSyntax;
    this.rightSyntax = rightSyntax;
    this.parentSyntax = returnSyntax;

    this.logicalForm = logicalForm;
    Preconditions.checkArgument(logicalForm == null || 
        StaticAnalysis.isLambda(logicalForm, 0) && StaticAnalysis.getLambdaArguments(logicalForm, 0).size() >= 2, 
          "Illegal logical form for binary rule: " + logicalForm);

    this.subjects = subjects.toArray(new String[0]);
    this.subjectSyntacticCategories = subjectSyntaxes.toArray(new HeadedSyntacticCategory[0]);
    this.argumentNumbers = Ints.toArray(argumentNumbers);
    this.objects = Ints.toArray(objects);
    
    this.type = Preconditions.checkNotNull(type);
  }

  /**
   * Parses a binary rule from a line in comma-separated format. The
   * expected fields, in order, are:
   * <ul>
   * <li>The headed syntactic categories to combine and return:
   * <code>(left syntax) (right syntax) (return syntax)</code>
   * <li>(optional) Logical form for the rule.
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
    String[] chunks = new CsvParser(',', CsvParser.DEFAULT_QUOTE,
        CsvParser.NULL_ESCAPE).parseLine(line.trim());
    Preconditions.checkArgument(chunks.length >= 1);

    String[] syntacticParts = chunks[0].split(" ");
    Preconditions.checkArgument(syntacticParts.length == 3);
    HeadedSyntacticCategory leftSyntax = HeadedSyntacticCategory.parseFrom(syntacticParts[0]);
    HeadedSyntacticCategory rightSyntax = HeadedSyntacticCategory.parseFrom(syntacticParts[1]);
    HeadedSyntacticCategory returnSyntax = HeadedSyntacticCategory.parseFrom(syntacticParts[2]);

    Expression2 logicalForm = null;
    if (chunks.length >= 2 && chunks[1].trim().length() > 0) {
      logicalForm = ExpressionParser.expression2().parse(chunks[1]);
    }

    // Parse the type of combinator, if one is given.
    Combinator.Type type = Combinator.Type.OTHER;
    if (chunks.length >= 3) {
      type = Combinator.Type.valueOf(chunks[2]);
    }

    // Parse any dependencies, if given.
    List<String> subjects = Lists.newArrayList();
    List<HeadedSyntacticCategory> subjectSyntacticCategories = Lists.newArrayList();
    List<Integer> argNums = Lists.newArrayList();
    List<Integer> objects = Lists.newArrayList();
    if (chunks.length >= 4) {  
      for (int i = 3; i < chunks.length; i++) {
        String[] newDeps = chunks[i].split(" ");
        Preconditions.checkArgument(newDeps.length == 3);
        subjects.add(newDeps[0]);
        subjectSyntacticCategories.add(rightSyntax.getCanonicalForm());
        argNums.add(Integer.parseInt(newDeps[1]));
        objects.add(Integer.parseInt(newDeps[2]));
      }
    }

    return new CcgBinaryRule(leftSyntax, rightSyntax, returnSyntax, logicalForm,
        subjects, subjectSyntacticCategories, argNums, objects, type);
  }
  
  
  /**
   * Reads in a collection of unary and binary rules, adding the rules
   * to {@code binaryRules} and {@code unaryRules}, respectively.
   * 
   * @param unfilteredRuleLines
   * @param binaryRules
   * @param unaryRules
   */
  public static void parseBinaryAndUnaryRules(Iterable<String> unfilteredRuleLines, 
      List<CcgBinaryRule> binaryRules, List<CcgUnaryRule> unaryRules) {
    for (String line : unfilteredRuleLines) {
      // System.out.println(line);
      if (!line.startsWith("#")) {
        try {
          binaryRules.add(CcgBinaryRule.parseFrom(line));
        } catch (IllegalArgumentException e) {
          unaryRules.add(CcgUnaryRule.parseFrom(line));
        }
      }
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
   * Gets the syntactic type that is produced by this rule. The
   * returned type may not be in canonical form.
   * 
   * @return
   */
  public HeadedSyntacticCategory getParentSyntacticType() {
    return parentSyntax;
  }

  /**
   * Gets a lambda calculus representation of the function of this
   * operation. The returned function accepts two arguments: the left
   * and right logical forms, in that order.
   * 
   * @return
   */
  public Expression2 getLogicalForm() {
    return logicalForm;
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
  
  public HeadedSyntacticCategory[] getSubjectSyntacticCategories() {
    return subjectSyntacticCategories;
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

  /**
   * Gets the type of combinator represented by this rule.
   * 
   * @return
   */
  public Combinator.Type getCombinatorType() {
    if (type == null) {
      // This check is included for backward compatibility with
      // serialized BinaryCombinators that do not include the
      // type field.
      return Combinator.Type.OTHER;
    } else {
      return type;
    }
  }

  @Override
  public String toString() {
    return leftSyntax + " " + rightSyntax + " -> " + parentSyntax + ", " + Arrays.toString(subjects);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + Arrays.hashCode(argumentNumbers);
    result = prime * result + ((leftSyntax == null) ? 0 : leftSyntax.hashCode());
    result = prime * result + ((logicalForm == null) ? 0 : logicalForm.hashCode());
    result = prime * result + Arrays.hashCode(objects);
    result = prime * result + ((parentSyntax == null) ? 0 : parentSyntax.hashCode());
    result = prime * result + ((rightSyntax == null) ? 0 : rightSyntax.hashCode());
    result = prime * result + Arrays.hashCode(subjects);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    CcgBinaryRule other = (CcgBinaryRule) obj;
    if (!Arrays.equals(argumentNumbers, other.argumentNumbers))
      return false;
    if (leftSyntax == null) {
      if (other.leftSyntax != null)
        return false;
    } else if (!leftSyntax.equals(other.leftSyntax))
      return false;
    if (logicalForm == null) {
      if (other.logicalForm != null)
        return false;
    } else if (!logicalForm.equals(other.logicalForm))
      return false;
    if (!Arrays.equals(objects, other.objects))
      return false;
    if (parentSyntax == null) {
      if (other.parentSyntax != null)
        return false;
    } else if (!parentSyntax.equals(other.parentSyntax))
      return false;
    if (rightSyntax == null) {
      if (other.rightSyntax != null)
        return false;
    } else if (!rightSyntax.equals(other.rightSyntax))
      return false;
    if (!Arrays.equals(subjects, other.subjects))
      return false;
    return true;
  }
}
