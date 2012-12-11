package com.jayantkrish.jklol.ccg;

import java.util.Arrays;

import com.google.common.base.Preconditions;

/**
 * A combinator combines a pair of syntactic categories into a result
 * syntactic category.
 * 
 * @author jayantk
 */
public class Combinator {
  private final HeadedSyntacticCategory syntax;
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


  public Combinator(HeadedSyntacticCategory syntax, int[] leftVariableRelabeling,
      int[] rightVariableRelabeling, int[] resultOriginalVars, int[] resultVariableRelabeling,
      int[] unifiedVariables, String[] subjects, int[] argumentNumbers, int[] objects) {
    this.syntax = syntax;
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
  }

  public HeadedSyntacticCategory getSyntax() {
    return syntax;
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
      dependencies[i] =  parser.unfilledDependencyToLong(dep);
    }
    return dependencies;
  }
  
  @Override
  public String toString() {
    return syntax + ":" + Arrays.toString(subjects);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + Arrays.hashCode(argumentNumbers);
    result = prime * result + Arrays.hashCode(leftVariableRelabeling);
    result = prime * result + Arrays.hashCode(objects);
    result = prime * result + Arrays.hashCode(resultOriginalVars);
    result = prime * result + Arrays.hashCode(resultVariableRelabeling);
    result = prime * result + Arrays.hashCode(rightVariableRelabeling);
    result = prime * result + Arrays.hashCode(subjects);
    result = prime * result + ((syntax == null) ? 0 : syntax.hashCode());
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
    if (syntax == null) {
      if (other.syntax != null)
        return false;
    } else if (!syntax.equals(other.syntax))
      return false;
    if (!Arrays.equals(unifiedVariables, other.unifiedVariables))
      return false;
    return true;
  }
}
