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

  // Backpointer information for the combinator, describing
  // the CCG operation which created it.
  private final boolean isArgumentOnLeft;
  private int argumentReturnDepth;
  // May be null, in which case the combinator did not originate
  // from a binary rule.
  private CcgBinaryRule binaryRule;

  public Combinator(int syntax, int[] syntaxUniqueVars, int[] leftVariableRelabeling,
      int[] rightVariableRelabeling, int[] resultOriginalVars, int[] resultVariableRelabeling,
      int[] unifiedVariables, String[] subjects, int[] argumentNumbers, int[] objects,
      boolean isArgumentOnLeft, int argumentReturnDepth, CcgBinaryRule binaryRule) {
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
    this.binaryRule = binaryRule;
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

  public CcgBinaryRule getBinaryRule() {
    return binaryRule;
  }

  @Override
  public String toString() {
    return syntax + ":" + Arrays.toString(subjects) + " " + isArgumentOnLeft;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + Arrays.hashCode(argumentNumbers);
    result = prime * result + argumentReturnDepth;
    result = prime * result + ((binaryRule == null) ? 0 : binaryRule.hashCode());
    result = prime * result + (isArgumentOnLeft ? 1231 : 1237);
    result = prime * result + Arrays.hashCode(leftVariableRelabeling);
    result = prime * result + Arrays.hashCode(objects);
    result = prime * result + Arrays.hashCode(resultOriginalVars);
    result = prime * result + Arrays.hashCode(resultVariableRelabeling);
    result = prime * result + Arrays.hashCode(rightVariableRelabeling);
    result = prime * result + Arrays.hashCode(subjects);
    result = prime * result + syntax;
    result = prime * result + Arrays.hashCode(syntaxUniqueVars);
    result = prime * result + Arrays.hashCode(unifiedVariables);
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
    Combinator other = (Combinator) obj;
    if (!Arrays.equals(argumentNumbers, other.argumentNumbers))
      return false;
    if (argumentReturnDepth != other.argumentReturnDepth)
      return false;
    if (binaryRule == null) {
      if (other.binaryRule != null)
        return false;
    } else if (!binaryRule.equals(other.binaryRule))
      return false;
    if (isArgumentOnLeft != other.isArgumentOnLeft)
      return false;
    if (!Arrays.equals(leftVariableRelabeling, other.leftVariableRelabeling))
      return false;
    if (!Arrays.equals(objects, other.objects))
      return false;
    if (!Arrays.equals(resultOriginalVars, other.resultOriginalVars))
      return false;
    if (!Arrays.equals(resultVariableRelabeling, other.resultVariableRelabeling))
      return false;
    if (!Arrays.equals(rightVariableRelabeling, other.rightVariableRelabeling))
      return false;
    if (!Arrays.equals(subjects, other.subjects))
      return false;
    if (syntax != other.syntax)
      return false;
    if (!Arrays.equals(syntaxUniqueVars, other.syntaxUniqueVars))
      return false;
    if (!Arrays.equals(unifiedVariables, other.unifiedVariables))
      return false;
    return true;
  }
}
