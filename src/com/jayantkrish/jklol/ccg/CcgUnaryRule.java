package com.jayantkrish.jklol.ccg;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.ccg.CcgChart.ChartEntry;
import com.jayantkrish.jklol.util.CsvParser;

public class CcgUnaryRule implements Serializable {

  private static final long serialVersionUID = 1L;

  private final HeadedSyntacticCategory inputSyntax;
  private final HeadedSyntacticCategory returnSyntax;

  public CcgUnaryRule(HeadedSyntacticCategory inputSyntax, HeadedSyntacticCategory returnSyntax) {
    this.inputSyntax = Preconditions.checkNotNull(inputSyntax);
    this.returnSyntax = Preconditions.checkNotNull(returnSyntax);
    
    Preconditions.checkArgument(returnSyntax.isCanonicalForm());

    // Ensure that the return type has all of the variables in
    // inputSyntax.
    Set<Integer> returnVars = Sets.newHashSet(Ints.asList(returnSyntax.getUniqueVariables()));
    Preconditions.checkState(returnVars.containsAll(
        Ints.asList(inputSyntax.getUniqueVariables())));
  }

  /**
   * Parses a unary rule from a line in comma-separated format. The
   * expected fields, in order, are:
   * <ul>
   * <li>The headed syntactic categories to combine and return:
   * <code>(input syntax) (return syntax)</code>
   * <li>(optional) Additional unfilled dependencies, in standard
   * format:
   * <code>(predicate) (argument number) (argument variable)</code>
   * </ul>
   * 
   * For example, "NP{0} S{1}/(S{1}\NP{0}){1}" is a unary type-raising
   * rule that allows an NP to combine with an adjacent verb.
   * 
   * @param line
   * @return
   */
  public static CcgUnaryRule parseFrom(String line) {
    String[] chunks = new CsvParser(CsvParser.DEFAULT_SEPARATOR,
        CsvParser.DEFAULT_QUOTE, CsvParser.NULL_ESCAPE).parseLine(line.trim());
    Preconditions.checkArgument(chunks.length >= 1);

    String[] syntacticParts = chunks[0].split(" ");
    Preconditions.checkArgument(syntacticParts.length == 2);
    HeadedSyntacticCategory inputSyntax = HeadedSyntacticCategory.parseFrom(syntacticParts[0]);
    HeadedSyntacticCategory returnSyntax = HeadedSyntacticCategory.parseFrom(syntacticParts[1]);

    // Ensure that the return syntactic type is in canonical form.
    HeadedSyntacticCategory returnCanonical = returnSyntax.getCanonicalForm();
    int[] originalToCanonical = returnSyntax.unifyVariables(returnSyntax.getUniqueVariables(), returnCanonical, new int[0]);

    int[] inputVars = inputSyntax.getUniqueVariables();
    int[] inputRelabeling = new int[inputVars.length];
    int[] returnOriginalVars = returnSyntax.getUniqueVariables();
    int nextUnassignedVar = Ints.max(returnCanonical.getUniqueVariables()) + 1;
    for (int i = 0; i < inputVars.length; i++) {
      int index = Ints.indexOf(returnOriginalVars, inputVars[i]);
      if (index != -1) {
        inputRelabeling[i] = originalToCanonical[index];
      } else {
        inputRelabeling[i] = nextUnassignedVar;
        nextUnassignedVar++;
      }
    }
    HeadedSyntacticCategory relabeledInput = inputSyntax.relabelVariables(inputVars, inputRelabeling);

    if (chunks.length >= 2) {
      throw new UnsupportedOperationException(
          "Using unfilled dependencies with unary CCG rules is not yet implemented");
      /*
       * String[] newDeps = chunks[4].split(" ");
       * Preconditions.checkArgument(newDeps.length == 3); long
       * subjectNum = Long.parseLong(newDeps[0].substring(1)); long
       * argNum = Long.parseLong(newDeps[1]); long objectNum =
       * Long.parseLong(newDeps[2].substring(1)); unfilledDeps = new
       * long[1];
       * 
       * unfilledDeps[0] =
       * CcgParser.marshalUnfilledDependency(objectNum, argNum,
       * subjectNum, 0, 0);
       */
    }
    return new CcgUnaryRule(relabeledInput, returnCanonical);
  }

  /**
   * Gets the syntactic category which this rule can be applied to. 
   * The returned category may not be in canonical form. 
   * 
   * @return
   */
  public HeadedSyntacticCategory getInputSyntacticCategory() {
    return inputSyntax;
  }
  
  /**
   * Gets the syntactic category that results from applying this rule.
   * The returned category may not be in canonical form.
   * 
   * @return
   */
  public HeadedSyntacticCategory getResultSyntacticCategory() {
    return returnSyntax;
  }

  /**
   * Gets the list of subjects of the dependencies instantiated by
   * this rule.
   * 
   * @return
   */
  public List<String> getSubjects() {
    return Collections.emptyList();
  }

  /**
   * Gets the list of argument numbers of the dependencies
   * instantiated by this rule.
   * 
   * @return
   */
  public List<Integer> getArgumentNumbers() {
    return Collections.emptyList();
  }

  /**
   * Gets the list of object variable numbers of the dependencies
   * instantiated by this rule.
   * 
   * @return
   */
  public List<Integer> getObjects() {
    return Collections.emptyList();
  }

  public ChartEntry apply(ChartEntry entry) {
    HeadedSyntacticCategory entrySyntax = entry.getHeadedSyntax();

    // Relabel entry's dependencies and variables to match the
    // assignments in the return type.
    int[] patternToChart = entrySyntax.unifyVariables(entrySyntax.getUniqueVariables(), inputSyntax,
        new int[0]);
    if (patternToChart == null) {
      return null;
    }

    int[] returnVars = entry.getAssignmentVariableNumsRelabeled(patternToChart);
    int[] returnPredicateNums = entry.getAssignmentPredicateNums();
    int[] returnIndexes = entry.getAssignmentIndexes();
    long[] returnUnfilledDeps = entry.getUnfilledDependenciesRelabeled(patternToChart);

    if (entry.isTerminal()) {
      return new ChartEntry(returnSyntax, entry.getLexiconEntry(), this, returnVars,
          returnPredicateNums, returnIndexes, returnUnfilledDeps, entry.getDependencies(),
          entry.getLeftSpanStart(), entry.getLeftSpanEnd());
    } else {
      return new ChartEntry(returnSyntax, this, returnVars, returnPredicateNums, returnIndexes,
          returnUnfilledDeps, entry.getDependencies(), entry.getLeftSpanStart(), entry.getLeftSpanEnd(),
          entry.getLeftChartIndex(), entry.getRightSpanStart(), entry.getRightSpanEnd(),
          entry.getRightChartIndex(), entry.getCombinator());
    }
  }
}