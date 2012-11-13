package com.jayantkrish.jklol.ccg;

import java.io.IOException;
import java.util.Set;

import au.com.bytecode.opencsv.CSVParser;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.ccg.CcgChart.ChartEntry;

public class CcgUnaryRule {

  private final HeadedSyntacticCategory inputSyntax;
  private final HeadedSyntacticCategory returnSyntax;

  public CcgUnaryRule(HeadedSyntacticCategory inputSyntax, HeadedSyntacticCategory returnSyntax) {
    this.inputSyntax = Preconditions.checkNotNull(inputSyntax);
    this.returnSyntax = Preconditions.checkNotNull(returnSyntax);

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
    try {
      String[] chunks = new CSVParser(CSVParser.DEFAULT_SEPARATOR,
          CSVParser.DEFAULT_QUOTE_CHARACTER, CSVParser.NULL_CHARACTER).parseLine(line.trim());
      Preconditions.checkArgument(chunks.length >= 1);

      String[] syntacticParts = chunks[0].split(" ");
      Preconditions.checkArgument(syntacticParts.length == 2);
      HeadedSyntacticCategory inputSyntax = HeadedSyntacticCategory.parseFrom(syntacticParts[0]);
      HeadedSyntacticCategory returnSyntax = HeadedSyntacticCategory.parseFrom(syntacticParts[1]);

      long[] unfilledDeps = new long[0];
      if (chunks.length >= 2) {
        throw new UnsupportedOperationException("Not yet implemented");
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

      return new CcgUnaryRule(inputSyntax, returnSyntax);
    } catch (IOException e) {
      throw new IllegalArgumentException("Illegal unary rule string: " + line, e);
    }
  }

  public SyntacticCategory getInputSyntacticCategory() {
    return inputSyntax.getSyntax();
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
          entry.getRightChartIndex());
    }
  }
}