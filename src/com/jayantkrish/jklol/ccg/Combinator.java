package com.jayantkrish.jklol.ccg;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/**
 * A combinator combines a pair of syntactic categories into a result
 * syntactic category.
 * 
 * @author jayantk
 */
public class Combinator implements Serializable {
  private static final long serialVersionUID = 2L;

  public static enum Type {
    FORWARD_APPLICATION, BACKWARD_APPLICATION, FORWARD_COMPOSITION, BACKWARD_COMPOSITION, OTHER,
    CONJUNCTION
  };

  private final int syntax;
  private final int[] syntaxUniqueVars;
  private final int syntaxHeadVar;

  private final int[] leftVariableRelabeling;
  private final int[] leftInverseRelabeling;
  private final int[] rightVariableRelabeling;
  private final int[] rightInverseRelabeling;
  private final int[] resultOriginalVars;
  private final int[] resultVariableRelabeling;
  private final int[] resultInverseRelabeling;
  private final int[] unifiedVariables;

  // Unfilled dependencies created by this operation.
  private final String[] subjects;
  private final HeadedSyntacticCategory[] subjectSyntacticCategories;
  private final int[] argumentNumbers;
  // The variables each dependency accepts.
  private final int[] objects;

  // Backpointer information for the combinator, describing
  // the CCG operation which created it.
  private final boolean isArgumentOnLeft;
  private final int argumentReturnDepth;
  // May be null, in which case the combinator did not originate
  // from a binary rule.
  private final CcgBinaryRule binaryRule;

  // The type of this combinator (e.g., forward application)
  private final Type type;

  public Combinator(int syntax, int[] syntaxUniqueVars, int syntaxHeadVar, int[] leftVariableRelabeling,
      int[] leftInverseRelabeling, int[] rightVariableRelabeling, int[] rightInverseRelabeling,
      int[] resultOriginalVars, int[] resultVariableRelabeling, int[] resultInverseRelabeling,
      int[] unifiedVariables, String[] subjects, HeadedSyntacticCategory[] subjectSyntacticCategories, 
      int[] argumentNumbers, int[] objects, boolean isArgumentOnLeft, int argumentReturnDepth,
      CcgBinaryRule binaryRule, Type type) {
    this.syntax = syntax;
    this.syntaxUniqueVars = syntaxUniqueVars;
    this.syntaxHeadVar = syntaxHeadVar;

    this.leftVariableRelabeling = leftVariableRelabeling;
    this.leftInverseRelabeling = leftInverseRelabeling;
    this.rightVariableRelabeling = rightVariableRelabeling;
    this.rightInverseRelabeling = rightInverseRelabeling;
    this.resultOriginalVars = resultOriginalVars;
    this.resultVariableRelabeling = resultVariableRelabeling;
    this.resultInverseRelabeling = resultInverseRelabeling;
    this.unifiedVariables = unifiedVariables;

    Preconditions.checkArgument(leftInverseRelabeling.length == rightInverseRelabeling.length);
    Preconditions.checkArgument(subjects.length == objects.length);
    Preconditions.checkArgument(subjects.length == subjectSyntacticCategories.length);
    Preconditions.checkArgument(subjects.length == argumentNumbers.length);
    for (int i = 0; i < subjectSyntacticCategories.length; i++) {
      Preconditions.checkArgument(subjectSyntacticCategories[i].isCanonicalForm());
    }
    
    this.subjects = subjects;
    this.subjectSyntacticCategories = subjectSyntacticCategories;
    this.argumentNumbers = argumentNumbers;
    this.objects = objects;

    this.isArgumentOnLeft = isArgumentOnLeft;
    this.argumentReturnDepth = argumentReturnDepth;
    this.binaryRule = binaryRule;
    
    this.type = Preconditions.checkNotNull(type);
  }

  public final int getSyntax() {
    return syntax;
  }

  public final int[] getSyntaxUniqueVars() {
    return syntaxUniqueVars;
  }
  
  public final int getSyntaxHeadVar() {
    return syntaxHeadVar;
  }

  public final int[] getLeftVariableRelabeling() {
    return leftVariableRelabeling;
  }
  
  public final int[] getLeftInverseRelabeling() {
    return leftInverseRelabeling;
  }

  public final int[] getRightVariableRelabeling() {
    return rightVariableRelabeling;
  }
  
  public final int[] getRightInverseRelabeling() {
    return rightInverseRelabeling;
  }

  public final int[] getResultOriginalVars() {
    return resultOriginalVars;
  }

  public final int[] getResultVariableRelabeling() {
    return resultVariableRelabeling;
  }

  public final int[] getResultInverseRelabeling() {
    return resultInverseRelabeling;
  }

  public final int[] getUnifiedVariables() {
    return unifiedVariables;
  }

  public final String[] getSubjects() {
    return subjects;
  }
  
  public final HeadedSyntacticCategory[] getSubjectSyntacticCategories() {
    return subjectSyntacticCategories;
  }

  public final int[] getArgumentNumbers() {
    return argumentNumbers;
  }

  public final int[] getObjects() {
    return objects;
  }

  public final boolean hasUnfilledDependencies() {
    return subjects.length > 0;
  }
  
  public final List<UnfilledDependency> getUnfilledDependencies(int headWordIndex) {
    List<UnfilledDependency> deps = Lists.newArrayList();
    for (int i = 0; i < subjects.length; i++) {
      deps.add(UnfilledDependency.createWithKnownSubject(subjects[i],
          subjectSyntacticCategories[i], headWordIndex, argumentNumbers[i], objects[i]));
    }
    return deps;
  }

  public final boolean isArgumentOnLeft() {
    return isArgumentOnLeft;
  }

  /**
   * Gets the type of this combinator, e.g., forward application.
   * 
   * @return
   */
  public final Type getType() {
    return type;
  }

  /**
   * This function returns the depth of the composition operation. If
   * 0, this is a function application. If > 0, it represents
   * composition where the returned number of arguments from the
   * argument category were not consumed by the combinator.
   * 
   * @return
   */
  public final int getArgumentReturnDepth() {
    return argumentReturnDepth;
  }

  public final CcgBinaryRule getBinaryRule() {
    return binaryRule;
  }

  public Combinator applicationToComposition(int depth) {
    return new Combinator(syntax, syntaxUniqueVars, syntaxHeadVar, leftVariableRelabeling,
        leftInverseRelabeling, rightVariableRelabeling, rightInverseRelabeling, resultOriginalVars,
        resultVariableRelabeling, resultInverseRelabeling, unifiedVariables, subjects,
        subjectSyntacticCategories, argumentNumbers, objects, isArgumentOnLeft, argumentReturnDepth + depth,
        binaryRule, type);
  }

  @Override
  public String toString() {
    if (binaryRule != null) {
      return binaryRule.toString();
    } else {
      return syntax + ":" + Arrays.toString(subjects) + " " + isArgumentOnLeft;
    }
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
    result = prime * result + Arrays.hashCode(subjectSyntacticCategories);
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
    if (!Arrays.equals(subjectSyntacticCategories, other.subjectSyntacticCategories))
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
