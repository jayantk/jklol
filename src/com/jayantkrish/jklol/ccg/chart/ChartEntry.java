package com.jayantkrish.jklol.ccg.chart;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.Combinator;
import com.jayantkrish.jklol.ccg.UnaryCombinator;


/**
 * An entry of a CCG parse chart, containing both a syntactic
 * and semantic type. The semantic type consists of yet-unfilled
 * semantic dependencies.
 * <p>
 * Chart entries also include any filled dependencies instantiated
 * during the parsing operation that produced the entry. Finally,
 * chart entries include backpointers to the chart entries used to
 * create them. These backpointers allow CCG parses to be
 * reconstructed from the chart.
 * 
 * @author jayant
 */
public class ChartEntry {
  // The syntactic category of the root of the parse span,
  // encoded as an integer.
  private final int syntax;
  private final int[] syntaxUniqueVars;

  // If non-null, this unary rule was applied at this entry to
  // produce syntax from the original category.
  private final UnaryCombinator rootUnaryRule;

  // If non-null, these rules were applied to the left / right
  // chart entries before the binary rule that produced this entry.
  private final UnaryCombinator leftUnaryRule;
  private final UnaryCombinator rightUnaryRule;

  // An assignment to the semantic variables in the syntactic category.
  // Each value is both a predicate and its index in the sentence.
  private final long[] assignments;

  // Partially complete dependency structures, encoded into longs
  // for efficiency.
  private final long[] unfilledDependencies;
  // Complete dependency structures, encoded into longs for
  // efficiency.
  private final long[] deps;
  
  // A hash code for the headed syntactic category, unfilled dependencies,
  // and assignments of this entry. Two chart entries with different hash
  // codes differ in at least one of these three values. 
  private final long syntaxHeadHashCode;

  // If this is a terminal, lexiconEntry contains the CcgCategory
  // from the lexicon used to create this chartEntry. This variable
  // is saved to track which lexicon entries are used in a parse,
  // for parameter estimation purposes.
  private final CcgCategory lexiconEntry;
  // If this is a terminal, this contains the words used to trigger
  // the category. This may be different from the words in the
  // sentence,
  // if the original words were not part of the lexicon.
  private final List<String> lexiconTriggerWords;

  // Backpointer information
  private final int leftSpanStart;
  private final int leftSpanEnd;
  private final int leftChartIndex;

  private final int rightSpanStart;
  private final int rightSpanEnd;
  private final int rightChartIndex;

  private final Combinator combinator;

  public ChartEntry(int syntax, int[] syntaxUniqueVars, UnaryCombinator rootUnaryRule, UnaryCombinator leftUnaryRule,
      UnaryCombinator rightUnaryRule, long[] assignments, long[] unfilledDependencies,
      long[] deps, int leftSpanStart, int leftSpanEnd, int leftChartIndex,
      int rightSpanStart, int rightSpanEnd, int rightChartIndex, Combinator combinator) {
    this.syntax = syntax;
    this.syntaxUniqueVars = syntaxUniqueVars;

    this.rootUnaryRule = rootUnaryRule;
    this.leftUnaryRule = leftUnaryRule;
    this.rightUnaryRule = rightUnaryRule;

    this.assignments = Preconditions.checkNotNull(assignments);
    this.unfilledDependencies = Preconditions.checkNotNull(unfilledDependencies);
    this.syntaxHeadHashCode = computeSyntaxHeadHashCode(syntax, assignments, unfilledDependencies);

    this.lexiconEntry = null;
    this.lexiconTriggerWords = null;
    this.deps = Preconditions.checkNotNull(deps);

    this.leftSpanStart = leftSpanStart;
    this.leftSpanEnd = leftSpanEnd;
    this.leftChartIndex = leftChartIndex;

    this.rightSpanStart = rightSpanStart;
    this.rightSpanEnd = rightSpanEnd;
    this.rightChartIndex = rightChartIndex;

    this.combinator = combinator;
  }

  public ChartEntry(int syntax, int[] syntaxUniqueVars, CcgCategory ccgCategory, List<String> terminalWords,
      UnaryCombinator rootUnaryRule,  long[] assignments, long[] unfilledDependencies, long[] deps,
      int spanStart, int spanEnd) {
    this.syntax = syntax;
    this.syntaxUniqueVars = syntaxUniqueVars;

    this.rootUnaryRule = rootUnaryRule;
    this.leftUnaryRule = null;
    this.rightUnaryRule = null;

    this.assignments = Preconditions.checkNotNull(assignments);
    this.unfilledDependencies = Preconditions.checkNotNull(unfilledDependencies);
    this.syntaxHeadHashCode = computeSyntaxHeadHashCode(syntax, assignments, unfilledDependencies);

    this.lexiconEntry = ccgCategory;
    this.lexiconTriggerWords = terminalWords;
    this.deps = Preconditions.checkNotNull(deps);

    // Use the leftSpan to represent the spanned terminal.
    this.leftSpanStart = spanStart;
    this.leftSpanEnd = spanEnd;
    this.leftChartIndex = -1;

    this.rightSpanStart = -1;
    this.rightSpanEnd = -1;
    this.rightChartIndex = -1;

    this.combinator = null;
  }

  public int getHeadedSyntax() {
    return syntax;
  }

  public int[] getHeadedSyntaxUniqueVars() {
    return syntaxUniqueVars;
  }

  /**
   * Gets the unary rule used to produce this chart entry. If no
   * rule was used, returns {@code null}.
   * 
   * @return
   */
  public UnaryCombinator getRootUnaryRule() {
    return rootUnaryRule;
  }

  public UnaryCombinator getLeftUnaryRule() {
    return leftUnaryRule;
  }

  public UnaryCombinator getRightUnaryRule() {
    return rightUnaryRule;
  }
  
  public long[] getAssignments() {
    return assignments;
  }
  
  public int[] getAssignmentPredicateNums() {
    int[] predicateNums = new int[assignments.length];
    for (int i = 0; i < assignments.length; i++) {
      predicateNums[i] = CcgParser.getAssignmentPredicateNum(assignments[i]);
    }
    return predicateNums;
  }

  /**
   * Replaces the {@code i}th unique variable in {@code this} with
   * the {@code i}th variable in {@code relabeling}.
   * 
   * @param relabeling
   * @return
   */
  public long[] getAssignmentsRelabeled(int[] relabeling) {
    int[] uniqueVars = syntaxUniqueVars;
    long[] relabeledAssignments = new long[assignments.length];
    Arrays.fill(relabeledAssignments, -1);
    int numFilled = 0;
    for (int i = 0; i < assignments.length; i++) {
      int assignmentVarNum = CcgParser.getAssignmentVarNum(assignments[i]);
      for (int j = 0; j < uniqueVars.length; j++) {
        if (uniqueVars[j] == assignmentVarNum) {
          if (relabeling[j] != -1) {
            relabeledAssignments[numFilled] = CcgParser.replaceAssignmentVarNum(assignments[i],
                assignmentVarNum, relabeling[j]);
            numFilled++;
          }
        }
      }
    }

    if (numFilled < assignments.length) {
      return Arrays.copyOf(relabeledAssignments, numFilled);
    } else {
      return relabeledAssignments;
    }
  }

  public long[] getUnfilledDependencies() {
    return unfilledDependencies;
  }

  public long[] getUnfilledDependenciesRelabeled(int[] relabeling) {
    long[] accumulator = new long[unfilledDependencies.length];
    int numFilled = getUnfilledDependenciesRelabeled(relabeling, accumulator, 0);
    Preconditions.checkState(numFilled != -1);
    return accumulator;
  }
  
  public int getUnfilledDependenciesRelabeled(int[] relabeling, long[] dependencyAccumulator, int accumulatorStartIndex) {
    if (dependencyAccumulator.length < accumulatorStartIndex + unfilledDependencies.length) {
      // The accumulator does not have enough space to store the dependencies
      // in this chart entry.
      return -1;
    }

    int numDepsRelabeled = 0;
    for (int i = 0; i < unfilledDependencies.length; i++) {
      long unfilledDependency = unfilledDependencies[i];
      int objectVarNum = CcgParser.getObjectArgNumFromDep(unfilledDependency);
      int j;
      for (j = 0; j < syntaxUniqueVars.length; j++) {
        if (syntaxUniqueVars[j] == objectVarNum) {
          // Relabel this variable unless the relabeling drops it.
          // Either way, the search for the variable is finished.
          if (relabeling[j] != -1) {
            unfilledDependency -= CcgParser.marshalUnfilledDependency(objectVarNum, 0, 0, 0, 0);
            unfilledDependency += CcgParser.marshalUnfilledDependency(relabeling[j], 0, 0, 0, 0);
            dependencyAccumulator[numDepsRelabeled + accumulatorStartIndex] = unfilledDependency;
            numDepsRelabeled++;
          }
          break;
        }
      }
      Preconditions.checkState(j != syntaxUniqueVars.length || relabeling[j] == -1,
          "No relabeling %s %s %s", syntax, i, objectVarNum);
    }

    return accumulatorStartIndex + numDepsRelabeled;
  }

  public long getSyntaxHeadHashCode() {
    return syntaxHeadHashCode;
  }

  public CcgCategory getLexiconEntry() {
    return lexiconEntry;
  }

  public List<String> getLexiconTriggerWords() {
    return lexiconTriggerWords;
  }

  public long[] getDependencies() {
    return deps;
  }

  public boolean isTerminal() {
    return rightChartIndex == -1;
  }

  public int getLeftSpanStart() {
    return leftSpanStart;
  }

  public int getLeftSpanEnd() {
    return leftSpanEnd;
  }

  public int getLeftChartIndex() {
    return leftChartIndex;
  }

  public int getRightSpanStart() {
    return rightSpanStart;
  }

  public int getRightSpanEnd() {
    return rightSpanEnd;
  }

  public int getRightChartIndex() {
    return rightChartIndex;
  }

  public Combinator getCombinator() {
    return combinator;
  }

  public ChartEntry applyUnaryRule(int resultSyntax, int[] resultUniqueVars,
      UnaryCombinator unaryRuleCombinator, long[] newAssignments, long[] newUnfilledDeps,
      long[] newFilledDeps) {
    Preconditions.checkState(rootUnaryRule == null);
    if (isTerminal()) {
      return new ChartEntry(resultSyntax, resultUniqueVars, lexiconEntry, lexiconTriggerWords,
          unaryRuleCombinator, newAssignments, newUnfilledDeps,
          newFilledDeps, leftSpanStart, leftSpanEnd);
    } else {
      return new ChartEntry(resultSyntax, resultUniqueVars, unaryRuleCombinator, leftUnaryRule, rightUnaryRule,
          newAssignments, newUnfilledDeps, newFilledDeps, leftSpanStart,
          leftSpanEnd, leftChartIndex, rightSpanStart, rightSpanEnd, rightChartIndex, combinator);
    }
  }

  @Override
  public String toString() {
    return "[" + Arrays.toString(assignments) + ":" + syntax
             + " " + Arrays.toString(deps) + " " + Arrays.toString(unfilledDependencies) + "]";
  }
  
  private static long computeSyntaxHeadHashCode(int syntax, long[] assignments,
      long[] unfilledDependencies) {

    long assignmentHashCode = 3;
    for (int i = 0; i < assignments.length; i++) {
      assignmentHashCode *= assignments[i];
    }
    
    long depHashCode = 5;
    for (int i = 0; i < unfilledDependencies.length; i++) {
      depHashCode *= unfilledDependencies[i];
    }
    
    return ((((long) syntax) * 31) + assignmentHashCode + depHashCode) * 63;
  }
}
