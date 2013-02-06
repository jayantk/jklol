package com.jayantkrish.jklol.ccg;

import java.io.Serializable;
import java.util.Arrays;

import com.google.common.base.Preconditions;

/**
 * A combinator combines a pair of syntactic categories into a result
 * syntactic category.
 * 
 * @author jayantk
 */
public class Combinator implements Serializable {
  private static final long serialVersionUID = 1L;

  private final int syntax;
  private final int[] syntaxUniqueVars;

  private final int[] leftVariableRelabeling;
  private final int[] rightVariableRelabeling;
  private final int[] resultOriginalVars;
  private final int[] resultVariableRelabeling;
  private final int[] unifiedVariables;

  // Unfilled dependencies created by this operation.
  private final String[] subjects;
  private final int[] argumentNumbers;
  // The variables each dependency accepts.
  private final int[] objects;

  private final boolean isArgumentOnLeft;
  private int argumentReturnDepth;

  public Combinator(int syntax, int[] syntaxUniqueVars, int[] leftVariableRelabeling,
      int[] rightVariableRelabeling, int[] resultOriginalVars, int[] resultVariableRelabeling,
      int[] unifiedVariables, String[] subjects, int[] argumentNumbers, int[] objects,
      boolean isArgumentOnLeft, int argumentReturnDepth) {
    this.syntax = syntax;
    this.syntaxUniqueVars = syntaxUniqueVars;

    this.leftVariableRelabeling = leftVariableRelabeling;
    this.rightVariableRelabeling = rightVariableRelabeling;
    this.resultOriginalVars = resultOriginalVars;
    this.resultVariableRelabeling = resultVariableRelabeling;
    this.unifiedVariables = unifiedVariables;

    Preconditions.checkArgument(subjects.length == objects.length);
    Preconditions.checkArgument(subjects.length == argumentNumbers.length);
    this.subjects = subjects;
    this.argumentNumbers = argumentNumbers;
    this.objects = objects;

    this.isArgumentOnLeft = isArgumentOnLeft;
    this.argumentReturnDepth = argumentReturnDepth;
  }

  public int getSyntax() {
    return syntax;
  }

  public int[] getSyntaxUniqueVars() {
    return syntaxUniqueVars;
  }

  public int[] getLeftVariableRelabeling() {
    return leftVariableRelabeling;
  }

  public int[] getRightVariableRelabeling() {
    return rightVariableRelabeling;
  }

  public int[] getResultOriginalVars() {
    return resultOriginalVars;
  }

  public int[] getResultVariableRelabeling() {
    return resultVariableRelabeling;
  }

  public int[] getUnifiedVariables() {
    return unifiedVariables;
  }

  public String[] getSubjects() {
    return subjects;
  }

  public long[] getUnfilledDependencies(CcgParser parser, int headWordIndex) {
    long[] dependencies = new long[subjects.length];
    for (int i = 0; i < subjects.length; i++) {
      UnfilledDependency dep = UnfilledDependency.createWithKnownSubject(subjects[i],
          headWordIndex, argumentNumbers[i], objects[i]);
      dependencies[i] = parser.unfilledDependencyToLong(dep);
    }
    return dependencies;
  }

  public boolean isArgumentOnLeft() {
    return isArgumentOnLeft;
  }

  /**
   * This function returns the depth of the composition operation. If
   * 0, this is a function application. If > 0, it represents
   * composition where the returned number of arguments from the
   * argument category were not consumed by the combinator.
   * 
   * @return
   */
  public int getArgumentReturnDepth() {
    return argumentReturnDepth;
  }

  @Override
  public String toString() {
    return syntax + ":" + Arrays.toString(subjects) + " " + isArgumentOnLeft;
  }

}
