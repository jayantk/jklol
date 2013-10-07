package com.jayantkrish.jklol.ccg;

import java.io.Serializable;

import com.google.common.base.Preconditions;

public class UnfilledDependency implements Serializable {
  private static final long serialVersionUID = 2L;

  // Subject is the word projecting the dependency. Null if the subject is unfilled. 
  private final IndexedPredicate subject;
  private final HeadedSyntacticCategory subjectSyntax;
  // Subject may be unfilled. If so, then this variable 
  // is the index of the argument which fills the subject role. 
  private final int subjectFunctionVarIndex;
  // If subject is a variable, then it is a function. This index
  // tracks which argument of the subject function is filled by the object role.
  // (i.e., which dependencies inherited from the subject are filled by the object.)
  private final int subjectArgIndex;

  // Object is the arguments of the projected dependency. Null if object is unfilled. 
  private final IndexedPredicate object;
  private final int objectArgumentIndex;

  public UnfilledDependency(IndexedPredicate subject, HeadedSyntacticCategory subjectSyntax, 
      int subjectFunctionVarIndex,  int subjectArgIndex, IndexedPredicate object,
      int objectIndex) {
    this.subject = subject;
    Preconditions.checkArgument(subjectSyntax.isCanonicalForm());
    this.subjectSyntax = subjectSyntax;
    this.subjectFunctionVarIndex = subjectFunctionVarIndex;
    this.subjectArgIndex = subjectArgIndex;
    this.object = object;
    this.objectArgumentIndex = objectIndex;
  }

  public static UnfilledDependency createWithKnownSubject(String subject, HeadedSyntacticCategory subjectSyntax, 
      int subjectHeadWordIndex, int subjectArgIndex, int objectIndex) {
    return new UnfilledDependency(new IndexedPredicate(subject, subjectHeadWordIndex), subjectSyntax,
        -1, subjectArgIndex, null, objectIndex);
  }

  public IndexedPredicate getSubject() {
    return subject;
  }
  
  public HeadedSyntacticCategory getSubjectSyntax() {
    return subjectSyntax;
  }

  public boolean hasSubject() {
    return subject != null;
  }

  public int getSubjectIndex() {
    return subjectFunctionVarIndex;
  }

  public IndexedPredicate getObject() {
    return object;
  }

  public boolean hasObject() {
    return object != null;
  }
  
  /**
   * Returns {@code true} if this dependency has both a subject and
   * an object.
   *  
   * @return
   */
  public boolean isFilledDependency() {
    return subject != null && object != null;
  }
  
  public DependencyStructure toDependencyStructure() {
    Preconditions.checkState(isFilledDependency());
    return new DependencyStructure(subject.getHead(), subject.getHeadIndex(), subjectSyntax,
        object.getHead(), object.getHeadIndex(), subjectArgIndex);
  }

  public int getObjectIndex() {
    return objectArgumentIndex;
  }

  public int getArgumentIndex() {
    return subjectArgIndex;
  }
      
  /**
   * Returns a new UnfilledDependency whose subject is {@code newSubjectIndex} 
   * and whose object is equal to {@code this}'s object.
   *  
   * @param newSubjectIndex
   * @return
   */
  /*
   * TODO: delete me.
  public UnfilledDependency replaceSubject(int newSubjectIndex) {
    return new UnfilledDependency(null, newSubjectIndex, subjectArgIndex, object, objectArgumentIndex);
  }
  */
  
  /**
   * Returns a new UnfilledDependency whose subject is equal to 
   * {@code newSubject} and whose object is equal to {@code this}' object.
   *  
   * @param newSubject
   * @param newSubjectSyntax
   * @return
   */
  public UnfilledDependency replaceSubject(IndexedPredicate newSubject,
      HeadedSyntacticCategory newSubjectSyntax) {
    return new UnfilledDependency(newSubject, newSubjectSyntax, -1, subjectArgIndex,
        object, objectArgumentIndex);
  }
  
  /**
   * Returns a new UnfilledDependency whose object is {@code newObjectIndex} 
   * and whose subject is equal to {@code this}'s subject.
   *  
   * @param newObjectIndex
   * @return
   */
  public UnfilledDependency replaceObject(int newObjectIndex) {
    return new UnfilledDependency(subject, subjectSyntax, subjectFunctionVarIndex, subjectArgIndex,
        null, newObjectIndex);
  }
  
  /**
   * Returns a new UnfilledDependency whose object is {@code newObject} 
   * and whose subject is equal to {@code this}'s subject.
   *  
   * @param newObject
   * @return
   */
  public UnfilledDependency replaceObject(IndexedPredicate newObject) {
    return new UnfilledDependency(subject, subjectSyntax, subjectFunctionVarIndex,
        subjectArgIndex, newObject, -1);
  }
  
  @Override
  public String toString() {
    if (hasObject()) {
      return getSubject() + " " + subjectArgIndex + " " + getObject();
    } else {
      return getSubject() + " " + subjectArgIndex + " " + objectArgumentIndex;
    }
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((object == null) ? 0 : object.hashCode());
    result = prime * result + objectArgumentIndex;
    result = prime * result + ((subject == null) ? 0 : subject.hashCode());
    result = prime * result + subjectArgIndex;
    result = prime * result + subjectFunctionVarIndex;
    result = prime * result + ((subjectSyntax == null) ? 0 : subjectSyntax.hashCode());
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
    UnfilledDependency other = (UnfilledDependency) obj;
    if (object == null) {
      if (other.object != null)
        return false;
    } else if (!object.equals(other.object))
      return false;
    if (objectArgumentIndex != other.objectArgumentIndex)
      return false;
    if (subject == null) {
      if (other.subject != null)
        return false;
    } else if (!subject.equals(other.subject))
      return false;
    if (subjectArgIndex != other.subjectArgIndex)
      return false;
    if (subjectFunctionVarIndex != other.subjectFunctionVarIndex)
      return false;
    if (subjectSyntax == null) {
      if (other.subjectSyntax != null)
        return false;
    } else if (!subjectSyntax.equals(other.subjectSyntax))
      return false;
    return true;
  }
}
